package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import com.merge.backend.global.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowingConsumer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException

// 근무 취소·담당자 변경 Command(V3)가 부를 대타 종료 함수 (ML-3-Q1, API-CATALOG §4)
// V3 Command 대신 "Shift를 잠근 Transaction 안에서 호출"로 흉내 낸다. 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
@RecordApplicationEvents
class SubstituteShiftChangeCloseTest {

    @Autowired
    private lateinit var substituteRequestService: SubstituteRequestService

    @Autowired
    private lateinit var substituteCandidateService: SubstituteCandidateService

    @Autowired
    private lateinit var substituteRequestRepository: SubstituteRequestRepository

    @Autowired
    private lateinit var substituteCandidateRepository: SubstituteCandidateRepository

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var testClock: TestClock

    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    @BeforeEach
    fun fixClock() {
        // 같은 Context를 쓰는 다른 테스트가 시간을 바꿨을 수 있으므로 매번 고정
        testClock.setInstant(FIXED_NOW)
    }

    @Test
    @DisplayName("OPEN 요청은 SHIFT_CHANGED로 종료되고 후보 상태는 그대로다")
    fun closesOpenRequestAndKeepsCandidates() {
        // given
        val f = createFixture()

        // when
        val closed = closeByShiftChange(f.shiftId)

        // then
        assertThat(closed).containsExactly(ShiftChangeClosedRequest(f.requestId, f.requesterMemberId, null))
        inTransaction {
            assertClosedByShiftChange(f.requestId)
            assertThat(candidateStatuses(f.candidateIds)).containsExactly(CandidateStatus.PENDING, CandidateStatus.PENDING)
        }
    }

    @Test
    @DisplayName("ACCEPTED 요청은 SHIFT_CHANGED로 종료되고 수락 기록은 남으며 수락한 후보를 알려 준다")
    fun closesAcceptedRequestAndKeepsAcceptedRecord() {
        // given: 종료 시각과 구분되도록 한 시간 전에 수락
        val f = createFixture()
        testClock.setInstant(FIXED_NOW.minusSeconds(3_600))
        substituteCandidateService.acceptCandidate(f.candidateIds[0], f.candidateUserIds[0])
        testClock.setInstant(FIXED_NOW)
        val respondedAt = inTransactionResult {
            requireNotNull(substituteCandidateRepository.findById(f.candidateIds[0]).orElseThrow().respondedAt)
        }

        // when
        val closed = closeByShiftChange(f.shiftId)

        // then
        assertThat(closed).containsExactly(
            ShiftChangeClosedRequest(f.requestId, f.requesterMemberId, f.candidateMemberIds[0]),
        )
        inTransaction {
            assertClosedByShiftChange(f.requestId)
            assertThat(candidateStatuses(f.candidateIds)).containsExactly(CandidateStatus.ACCEPTED, CandidateStatus.PENDING)
            // 응답 이력(수락 시각)도 그대로
            assertThat(respondedAt).isEqualTo(now().minusHours(1))
            assertThat(substituteCandidateRepository.findById(f.candidateIds[0]).orElseThrow().respondedAt).isEqualTo(respondedAt)
        }
    }

    @Test
    @DisplayName("Lock 전에 요청을 먼저 읽어 둔 Transaction에서도 그사이 수락한 후보를 알려 준다")
    fun reportsAcceptedCandidateEvenIfRequestWasReadBeforeLock() {
        // given
        val f = createFixture()
        val requestRead = CountDownLatch(1)
        val accepted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(1)

        try {
            // when: Lock 없이 요청을 먼저 읽음(OPEN) → 그사이 후보 수락 Commit → Shift 잠그고 종료
            val future: Future<List<ShiftChangeClosedRequest>> = executor.submit(
                Callable {
                    inTransactionResult {
                        substituteRequestRepository.findById(f.requestId).orElseThrow()
                        requestRead.countDown()
                        await(accepted)
                        shiftRepository.findByIdForUpdate(f.shiftId).orElseThrow()
                        substituteRequestService.closeActiveRequestsByShiftChange(f.shiftId, now())
                    }
                }
            )
            assertThat(requestRead.await(10, SECONDS)).isTrue()
            substituteCandidateService.acceptCandidate(f.candidateIds[0], f.candidateUserIds[0])
            accepted.countDown()
            val closed = future.get(30, SECONDS)

            // then: 먼저 읽은 Entity는 OPEN이었지만 수락한 후보를 놓치지 않음
            assertThat(closed).containsExactly(
                ShiftChangeClosedRequest(f.requestId, f.requesterMemberId, f.candidateMemberIds[0]),
            )
            inTransaction { assertClosedByShiftChange(f.requestId) }
        } finally {
            accepted.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("종료 뒤 같은 근무로 새 대타 요청을 만들 수 있다")
    fun requesterCanCreateNewRequestAfterClose() {
        // given
        val f = createFixture()
        closeByShiftChange(f.shiftId)

        // when
        val response = substituteRequestService.create(f.shiftId, f.requesterUserId)

        // then
        assertThat(response.requestCreated).isTrue()
    }

    @Test
    @DisplayName("종료 뒤 대기 중이던 후보는 수락할 수 없다")
    fun pendingCandidateCannotAcceptAfterClose() {
        // given
        val f = createFixture()
        closeByShiftChange(f.shiftId)

        // when & then
        assertThatThrownBy { substituteCandidateService.acceptCandidate(f.candidateIds[0], f.candidateUserIds[0]) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(SubstituteRequestErrorCode.REQUEST_NOT_OPEN)
        inTransaction { assertClosedByShiftChange(f.requestId) }
    }

    @Test
    @DisplayName("APPROVED 요청은 승인 이력으로 그대로 두고 종료 목록에 넣지 않는다")
    fun leavesApprovedRequestAsIs() {
        // given
        val f = createFixture()
        substituteCandidateService.acceptCandidate(f.candidateIds[0], f.candidateUserIds[0])
        substituteRequestService.approve(f.requestId, f.managerUserId)

        // when
        val closed = closeByShiftChange(f.shiftId)

        // then
        assertThat(closed).isEmpty()
        inTransaction {
            val request = findRequest(f.requestId)
            assertThat(request.status).isEqualTo(RequestStatus.APPROVED)
            assertThat(request.closeReason).isNull()
            assertThat(request.approvedAt).isEqualTo(now())
            assertThat(shiftRepository.findById(f.shiftId).orElseThrow().member.id).isEqualTo(f.candidateMemberIds[0])
        }
    }

    @Test
    @DisplayName("이미 CLOSED인 요청은 그대로 두고 진행 중인 요청만 종료한다")
    fun leavesClosedRequestAndClosesOnlyActive() {
        // given: 관리자가 하루 전에 닫은 요청 + 그 뒤 새로 만든 요청
        val f = createFixture()
        testClock.setInstant(FIXED_NOW.minusSeconds(86_400))
        substituteRequestService.close(f.requestId, f.managerUserId)
        testClock.setInstant(FIXED_NOW)
        val newRequestId = requireNotNull(substituteRequestService.create(f.shiftId, f.requesterUserId).requestId)

        // when
        val closed = closeByShiftChange(f.shiftId)

        // then
        assertThat(closed.map { it.requestId }).containsExactly(newRequestId)
        inTransaction {
            val old = findRequest(f.requestId)
            assertThat(old.status).isEqualTo(RequestStatus.CLOSED)
            assertThat(old.closeReason).isEqualTo(RequestCloseReason.MANAGER_CLOSED)
            assertThat(old.closedAt).isEqualTo(now().minusDays(1))
            assertClosedByShiftChange(newRequestId)
        }
    }

    @Test
    @DisplayName("진행 중 요청이 2건 이상인 데이터도 모두 ID 순서로 종료한다")
    fun closesAllActiveRequestsInIdOrder() {
        // given: 서비스 검사로는 생기지 않는 비정상 데이터 (근무당 진행 중 1건 제한은 DB 제약이 아님)
        val f = createFixture()
        val secondRequestId = inTransactionResult {
            val shift = shiftRepository.findById(f.shiftId).orElseThrow()
            val requester = workplaceMemberRepository.findById(f.requesterMemberId).orElseThrow()
            requireNotNull(substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN)).id)
        }

        // when
        val closed = closeByShiftChange(f.shiftId)

        // then
        assertThat(closed.map { it.requestId }).containsExactly(f.requestId, secondRequestId)
        inTransaction {
            assertClosedByShiftChange(f.requestId)
            assertClosedByShiftChange(secondRequestId)
        }
    }

    @Test
    @DisplayName("종료된 요청은 수락·거절·승인·관리자 종료로 다시 열리지 않는다")
    fun closedRequestCannotBeReopened() {
        // given
        val f = createFixture()
        closeByShiftChange(f.shiftId)

        // when
        val errors = listOf(
            capture { substituteCandidateService.acceptCandidate(f.candidateIds[0], f.candidateUserIds[0]) },
            capture { substituteCandidateService.rejectCandidate(f.candidateIds[1], f.candidateUserIds[1]) },
            capture { substituteRequestService.approve(f.requestId, f.managerUserId) },
            capture { substituteRequestService.close(f.requestId, f.managerUserId) },
        )

        // then
        assertThat(errors).allSatisfy(ThrowingConsumer { assertThat(it).isInstanceOf(BusinessException::class.java) })
        inTransaction {
            assertClosedByShiftChange(f.requestId)
            assertThat(candidateStatuses(f.candidateIds)).containsExactly(CandidateStatus.PENDING, CandidateStatus.PENDING)
        }
    }

    @Test
    @DisplayName("대타 이벤트를 발행하지 않는다 (MANAGER_CLOSED 알림이 함께 나가지 않음)")
    fun publishesNoSubstituteEvent() {
        // given
        val f = createFixture()
        substituteCandidateService.acceptCandidate(f.candidateIds[0], f.candidateUserIds[0])
        val before = substituteEventCount()
        // 수락 이벤트가 세어졌는지로 셈 자체가 동작하는지 확인
        assertThat(before).isPositive()

        // when
        closeByShiftChange(f.shiftId)

        // then
        assertThat(substituteEventCount()).isEqualTo(before)
    }

    @Test
    @DisplayName("Transaction 없이 부르면 거부하고 아무것도 바꾸지 않는다")
    fun requiresCallerTransaction() {
        // given
        val f = createFixture()

        // when & then
        assertThatThrownBy { substituteRequestService.closeActiveRequestsByShiftChange(f.shiftId, now()) }
            .isInstanceOf(IllegalTransactionStateException::class.java)
        inTransaction { assertThat(findRequest(f.requestId).status).isEqualTo(RequestStatus.OPEN) }
    }

    @Test
    @DisplayName("후보 수락이 Commit 전이면 기다렸다가 수락된 상태를 보고 종료한다")
    fun waitsForAcceptInProgress() {
        // given
        val f = createFixture()
        var closed: List<ShiftChangeClosedRequest> = emptyList()

        // when: 수락이 Request를 잡고 Commit 전 → 그동안 근무 변경(Shift 잠금 후 종료 함수)
        val error = runSecondWhileFirstHoldsLock(
            first = { substituteCandidateService.acceptCandidate(f.candidateIds[0], f.candidateUserIds[0]) },
            second = { closed = closeByShiftChange(f.shiftId) },
        )

        // then: 수락이 먼저 반영된 상태로 종료, 수락한 후보를 알려 줌
        assertThat(error).isNull()
        assertThat(closed).containsExactly(
            ShiftChangeClosedRequest(f.requestId, f.requesterMemberId, f.candidateMemberIds[0]),
        )
        inTransaction {
            assertClosedByShiftChange(f.requestId)
            assertThat(candidateStatuses(f.candidateIds)).containsExactly(CandidateStatus.ACCEPTED, CandidateStatus.PENDING)
        }
    }

    // ===================== 실행 헬퍼 =====================

    // V3 Command처럼 Shift를 먼저 잠근 Transaction 안에서 호출
    private fun closeByShiftChange(shiftId: Long): List<ShiftChangeClosedRequest> =
        inTransactionResult {
            shiftRepository.findByIdForUpdate(shiftId).orElseThrow()
            substituteRequestService.closeActiveRequestsByShiftChange(shiftId, now())
        }

    // first를 Commit하지 않은 채로 멈추고 second를 실행. second가 Lock을 기다리는지 확인한 뒤 first를 Commit
    // (SubstituteRequestConcurrencyTest와 같은 방식)
    private fun runSecondWhileFirstHoldsLock(first: () -> Unit, second: () -> Unit): Throwable? {
        val executor = Executors.newFixedThreadPool(2)
        val firstExecuted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        try {
            val firstFuture: Future<*> = executor.submit(
                Runnable {
                    TransactionTemplate(transactionManager).executeWithoutResult {
                        first()
                        firstExecuted.countDown()
                        await(releaseFirst) // Commit하지 않고 대기
                    }
                }
            )

            assertThat(firstExecuted.await(10, SECONDS))
                .describedAs("first 작업이 Commit 전 상태로 대기해야 한다")
                .isTrue()

            val secondFuture: Future<Throwable?> = executor.submit(Callable { capture(second) })

            assertThatThrownBy { secondFuture.get(LOCK_WAIT_CHECK_MILLIS, MILLISECONDS) }
                .describedAs("first가 Commit하기 전에는 second가 Lock을 기다려야 한다")
                .isInstanceOf(TimeoutException::class.java)

            releaseFirst.countDown()
            firstFuture.get(10, SECONDS)

            return secondFuture.get(10, SECONDS)
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    // ===================== 검증 헬퍼 =====================

    private fun inTransaction(assertion: () -> Unit) {
        TransactionTemplate(transactionManager).executeWithoutResult { assertion() }
    }

    private fun <T : Any> inTransactionResult(block: () -> T): T =
        requireNotNull(TransactionTemplate(transactionManager).execute { block() })

    private fun findRequest(requestId: Long): SubstituteRequest =
        substituteRequestRepository.findById(requestId).orElseThrow()

    private fun assertClosedByShiftChange(requestId: Long) {
        val request = findRequest(requestId)
        assertThat(request.status).isEqualTo(RequestStatus.CLOSED)
        assertThat(request.closeReason).isEqualTo(RequestCloseReason.SHIFT_CHANGED)
        assertThat(request.closedAt).isEqualTo(now())
    }

    private fun candidateStatuses(candidateIds: List<Long>): List<CandidateStatus> =
        candidateIds
            .map { substituteCandidateRepository.findById(it).orElseThrow() }
            .map { it.status }

    // 대타 도메인 이벤트 수 (com.merge.backend.domain.substitute.event)
    private fun substituteEventCount(): Long =
        applicationEvents.stream(Any::class.java)
            .filter { it.javaClass.packageName == SUBSTITUTE_EVENT_PACKAGE }
            .count()

    // ===================== Fixture =====================

    private data class Fixture(
        val shiftId: Long,
        val requestId: Long,
        val requesterUserId: Long,
        val requesterMemberId: Long,
        val managerUserId: Long,
        val candidateIds: List<Long>,
        val candidateUserIds: List<Long>,
        val candidateMemberIds: List<Long>,
    )

    // 관리자 1명, 요청자 1명의 공개 근무, OPEN Request 1개 + PENDING 후보 2명
    private fun createFixture(): Fixture = inTransactionResult {
        val suffix = uniqueSuffix()
        val workplace = workplaceRepository.save(Workplace("근무 변경 종료 테스트 매장 $suffix", "SFC-$suffix"))
        val schedule = savePublishedSchedule(workplace)

        val manager = saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
        val requester = saveMember(workplace, "requester-$suffix", WorkplaceRole.EMPLOYEE)
        val shift = shiftRepository.save(
            Shift(schedule, requester, shiftStartAt(), shiftStartAt().plusHours(4), ShiftStatus.SCHEDULED)
        )
        val request = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))

        val candidateMembers = (0 until 2).map { saveMember(workplace, "candidate$it-$suffix", WorkplaceRole.EMPLOYEE) }
        val candidates = candidateMembers.map { substituteCandidateRepository.save(SubstituteCandidate(request, it)) }

        Fixture(
            shiftId = requireNotNull(shift.id),
            requestId = requireNotNull(request.id),
            requesterUserId = requireNotNull(requester.user.id),
            requesterMemberId = requireNotNull(requester.id),
            managerUserId = requireNotNull(manager.user.id),
            candidateIds = candidates.map { requireNotNull(it.id) },
            candidateUserIds = candidateMembers.map { requireNotNull(it.user.id) },
            candidateMemberIds = candidateMembers.map { requireNotNull(it.id) },
        )
    }

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    // 일주일 뒤 10:00
    private fun shiftStartAt(): LocalDateTime =
        now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)

    private fun savePublishedSchedule(workplace: Workplace): Schedule {
        val schedule = Schedule(
            workplace,
            shiftStartAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        )
        schedule.publish(now())
        return scheduleRepository.save(schedule)
    }

    private fun saveMember(workplace: Workplace, name: String, role: WorkplaceRole): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, now().minusDays(30)))
    }

    companion object {
        // second 작업이 이 시간 안에 끝나지 않으면 "Lock 대기 중"으로 판단
        private const val LOCK_WAIT_CHECK_MILLIS = 1_000L

        private const val SUBSTITUTE_EVENT_PACKAGE = "com.merge.backend.domain.substitute.event"

        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")

        private fun uniqueSuffix(): String = UUID.randomUUID().toString().substring(0, 8)

        private fun capture(task: () -> Unit): Throwable? =
            try {
                task()
                null
            } catch (throwable: Throwable) {
                throwable
            }

        private fun await(latch: CountDownLatch) {
            try {
                check(latch.await(30, SECONDS)) { "대기 시간 초과" }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException(e)
            }
        }
    }
}
