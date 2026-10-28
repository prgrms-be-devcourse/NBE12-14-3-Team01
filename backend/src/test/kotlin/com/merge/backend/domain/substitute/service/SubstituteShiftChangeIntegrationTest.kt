package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.dto.AcknowledgedUnavailableConflict
import com.merge.backend.domain.shift.dto.ShiftReassignRequest
import com.merge.backend.domain.shift.dto.UnavailableOverlap
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftAssignmentReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.entity.UnavailableTime
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftAssignmentHistoryRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.shift.service.ShiftService
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
import com.merge.backend.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
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

// V3 공개 근무 취소(API-PS-02)·재배정(API-PS-03)이 V4 대타 종료 함수를 실제로 부를 때의 대타 쪽 결과 (V3-N-33의 대타 부분, H6, ML-5)
// V3 Command를 그대로 호출하고 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class SubstituteShiftChangeIntegrationTest {

    @Autowired
    private lateinit var shiftService: ShiftService

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
    private lateinit var shiftAssignmentHistoryRepository: ShiftAssignmentHistoryRepository

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var unavailableTimeRepository: UnavailableTimeRepository

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

    @BeforeEach
    fun fixClock() {
        // 같은 Context를 쓰는 다른 테스트가 시간을 바꿨을 수 있으므로 매번 고정
        testClock.setInstant(FIXED_NOW)
    }

    // ===================== 경로 =====================

    @Test
    @DisplayName("공개 근무를 취소하면 수락된 대타 요청이 SHIFT_CHANGED로 닫히고 후보 상태는 그대로이며 같은 근무로 다시 요청할 수 없고 수락자는 종료된 대타 조회에서 확인한다")
    fun cancelClosesAcceptedRequest() {
        // given: B 수락, C 대기
        val f = createFixture(withRequest = true)
        accept(f, 0)

        // when
        val result = shiftService.cancelPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, f.managerUserId)

        // then
        assertThat(result.closedSubstituteRequests).isEqualTo(1)
        inTransaction {
            assertThat(findShift(f.shiftId).status).isEqualTo(ShiftStatus.CANCELLED)
            assertClosedByShiftChange(f.requestId)
            assertThat(candidateStatuses(f)).containsExactly(CandidateStatus.ACCEPTED, CandidateStatus.PENDING)
        }
        assertBusinessError(SubstituteRequestErrorCode.SHIFT_CANCELLED) {
            substituteRequestService.create(f.shiftId, f.requesterUserId)
        }

        // then: 수락자 B는 종료된 대타 조회(API-SB-01)에서 SHIFT_CHANGED로 확인
        val closedForAccepter = substituteCandidateService.findAcceptedClosedRequests(f.candidates[0].userId)
        assertThat(closedForAccepter.map { it.requestId }).containsExactly(f.requestId)
        assertThat(closedForAccepter[0].closeReason).isEqualTo(RequestCloseReason.SHIFT_CHANGED)
    }

    @Test
    @DisplayName("공개 근무를 다른 직원에게 재배정하면 진행 중 대타 요청이 SHIFT_CHANGED로 닫히고 새 담당은 다시 요청할 수 있으며 대기 후보는 수락할 수 없다")
    fun reassignClosesOpenRequest() {
        // given: B·C 대기
        val f = createFixture(withRequest = true)

        // when: A → D
        val result = shiftService.reassignPublishedShift(
            f.workplaceId, f.scheduleId, f.shiftId, reassignTo(f.targetMemberId), f.managerUserId,
        )

        // then
        assertThat(result.closedSubstituteRequests).isEqualTo(1)
        inTransaction {
            assertThat(findShift(f.shiftId).member.id).isEqualTo(f.targetMemberId)
            assertClosedByShiftChange(f.requestId)
            assertThat(candidateStatuses(f)).containsOnly(CandidateStatus.PENDING)
        }
        assertThat(substituteRequestService.create(f.shiftId, f.targetUserId).requestCreated).isTrue()
        assertBusinessError(SubstituteRequestErrorCode.REQUEST_NOT_OPEN) {
            substituteCandidateService.acceptCandidate(f.candidates[0].candidateId, f.candidates[0].userId)
        }
    }

    @Test
    @DisplayName("수락자가 불가능 시간을 등록해 승인이 막힌 뒤 관리자가 그 수락자에게 직접 재배정하면 요청은 승인이 아니라 SHIFT_CHANGED로 닫힌다")
    fun reassignToBlockedAccepterClosesAsShiftChanged() {
        // given: B 수락 → B가 근무 시간과 겹치는 불가능 시간 등록 → 승인 거부 (D21-T3)
        val f = createFixture(withRequest = true)
        accept(f, 0)
        val accepter = f.candidates[0]
        inTransaction {
            unavailableTimeRepository.save(
                UnavailableTime(userRepository.findById(accepter.userId).orElseThrow(), f.startAt.minusHours(1), f.startAt.plusHours(2))
            )
        }
        assertBusinessError(SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME) {
            substituteRequestService.approve(f.requestId, f.managerUserId)
        }

        // when: 관리자가 불가능 시간을 확인하고 B에게 재배정
        val request = ShiftReassignRequest(
            accepter.memberId,
            listOf(
                AcknowledgedUnavailableConflict(
                    f.shiftId, accepter.memberId, listOf(UnavailableOverlap(f.startAt, f.startAt.plusHours(2))),
                ),
            ),
        )
        shiftService.reassignPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, request, f.managerUserId)

        // then
        inTransaction {
            assertClosedByShiftChange(f.requestId)
            assertThat(findCandidate(accepter.candidateId).status).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(findShift(f.shiftId).member.id).isEqualTo(accepter.memberId)
            val histories = shiftAssignmentHistoryRepository.findAllByShift_IdOrderByCreateDateAsc(f.shiftId)
            assertThat(histories).hasSize(1)
            assertThat(histories[0].reason).isEqualTo(ShiftAssignmentReason.MANAGER_REASSIGNED)
            assertThat(histories[0].newMember.id).isEqualTo(accepter.memberId)
        }
    }

    // ===================== 근무 변경 × 대타 생성 =====================

    @Test
    @DisplayName("취소가 근무를 잡고 있는 동안 대타 생성은 기다렸다가 SHIFT_CANCELLED로 거부되고 진행 중 요청은 0건이다")
    fun createWaitsForCancelInProgress() {
        // given
        val f = createFixture(withRequest = false)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { shiftService.cancelPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, f.managerUserId) },
            second = { substituteRequestService.create(f.shiftId, f.requesterUserId) },
        )

        // then
        assertBusinessError(error, SubstituteRequestErrorCode.SHIFT_CANCELLED)
        assertThat(activeRequestCount(f.shiftId)).isZero()
    }

    @Test
    @DisplayName("재배정이 근무를 잡고 있는 동안 원래 담당의 대타 생성은 기다렸다가 거부되고 진행 중 요청은 0건이다")
    fun createWaitsForReassignInProgress() {
        // given
        val f = createFixture(withRequest = false)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = {
                shiftService.reassignPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, reassignTo(f.targetMemberId), f.managerUserId)
            },
            second = { substituteRequestService.create(f.shiftId, f.requesterUserId) },
        )

        // then: 담당이 바뀌어 원래 담당은 요청할 수 없음
        assertBusinessError(error, SubstituteRequestErrorCode.NOT_OWN_SHIFT)
        assertThat(activeRequestCount(f.shiftId)).isZero()
    }

    @Test
    @DisplayName("대타 생성이 근무를 잡고 있는 동안 재배정은 기다렸다가 새 요청을 SHIFT_CHANGED로 닫는다")
    fun reassignWaitsForCreateInProgress() {
        // given
        val f = createFixture(withRequest = false)
        var closed = -1

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { substituteRequestService.create(f.shiftId, f.requesterUserId) },
            second = {
                closed = shiftService.reassignPublishedShift(
                    f.workplaceId, f.scheduleId, f.shiftId, reassignTo(f.targetMemberId), f.managerUserId,
                ).closedSubstituteRequests
            },
        )

        // then
        assertThat(error).isNull()
        assertThat(closed).isEqualTo(1)
        assertThat(activeRequestCount(f.shiftId)).isZero()
        inTransaction {
            val requests = substituteRequestRepository.findAll().filter { it.shift.id == f.shiftId }
            assertThat(requests).hasSize(1)
            assertThat(requests[0].closeReason).isEqualTo(RequestCloseReason.SHIFT_CHANGED)
        }
    }

    // ===================== 재배정 × 수락 (H6-C1) =====================

    @Test
    @DisplayName("후보 수락이 Commit 전이면 재배정이 기다렸다가 수락된 요청을 SHIFT_CHANGED로 닫고 승인은 거부된다")
    fun reassignWaitsForAcceptInProgress() {
        // given
        val f = createFixture(withRequest = true)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { accept(f, 0) },
            second = {
                shiftService.reassignPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, reassignTo(f.targetMemberId), f.managerUserId)
            },
        )

        // then
        assertThat(error).isNull()
        inTransaction {
            assertClosedByShiftChange(f.requestId)
            assertThat(candidateStatuses(f)).containsExactly(CandidateStatus.ACCEPTED, CandidateStatus.PENDING)
            assertThat(findShift(f.shiftId).member.id).isEqualTo(f.targetMemberId)
        }
        assertBusinessError(SubstituteRequestErrorCode.INVALID_REQUEST) {
            substituteRequestService.approve(f.requestId, f.managerUserId)
        }
    }

    @Test
    @DisplayName("재배정이 Commit 전이면 후보 수락이 기다렸다가 REQUEST_NOT_OPEN으로 거부된다")
    fun acceptWaitsForReassignInProgress() {
        // given
        val f = createFixture(withRequest = true)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = {
                shiftService.reassignPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, reassignTo(f.targetMemberId), f.managerUserId)
            },
            second = { accept(f, 0) },
        )

        // then
        assertBusinessError(error, SubstituteRequestErrorCode.REQUEST_NOT_OPEN)
        inTransaction {
            assertClosedByShiftChange(f.requestId)
            assertThat(candidateStatuses(f)).containsOnly(CandidateStatus.PENDING)
        }
    }

    // ===================== 재배정 × 승인 (H6-C2) =====================

    @Test
    @DisplayName("승인이 Commit 전이면 재배정이 기다렸다가 승인된 근무를 재배정하고 닫을 대타 요청은 없다")
    fun reassignWaitsForApproveInProgress() {
        // given
        val f = createFixture(withRequest = true)
        accept(f, 0)
        var closed = -1

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { substituteRequestService.approve(f.requestId, f.managerUserId) },
            second = {
                closed = shiftService.reassignPublishedShift(
                    f.workplaceId, f.scheduleId, f.shiftId, reassignTo(f.targetMemberId), f.managerUserId,
                ).closedSubstituteRequests
            },
        )

        // then: 승인 → 재배정 순서로 정리, 최종 담당 1명
        assertThat(error).isNull()
        assertThat(closed).isZero()
        inTransaction {
            assertThat(findRequest(f.requestId).status).isEqualTo(RequestStatus.APPROVED)
            assertThat(findShift(f.shiftId).member.id).isEqualTo(f.targetMemberId)
            val histories = shiftAssignmentHistoryRepository.findAllByShift_IdOrderByCreateDateAsc(f.shiftId)
            assertThat(histories.map { it.previousMember.id }).containsExactly(f.candidates[0].memberId)
        }
    }

    @Test
    @DisplayName("재배정이 Commit 전이면 승인이 기다렸다가 INVALID_REQUEST로 거부되고 담당은 재배정 대상이다")
    fun approveWaitsForReassignInProgress() {
        // given
        val f = createFixture(withRequest = true)
        accept(f, 0)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = {
                shiftService.reassignPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, reassignTo(f.targetMemberId), f.managerUserId)
            },
            second = { substituteRequestService.approve(f.requestId, f.managerUserId) },
        )

        // then
        assertBusinessError(error, SubstituteRequestErrorCode.INVALID_REQUEST)
        inTransaction {
            assertClosedByShiftChange(f.requestId)
            assertThat(findShift(f.shiftId).member.id).isEqualTo(f.targetMemberId)
        }
    }

    // ===================== 다른 근무지 수락 × 재배정 (ML5-T4) =====================

    @Test
    @DisplayName("다른 근무지 대타 수락이 Commit 전이면 그 직원에게의 재배정이 기다렸다가 진행되고, 수락한 대타의 승인은 근무가 겹쳐 거부된다")
    fun reassignWaitsForOtherWorkplaceAccept() {
        // given: W2 대타 요청의 후보 B, W1의 같은 시간 근무를 B에게 재배정할 예정 (B는 W1에도 소속)
        val w2 = createFixture(withRequest = true)
        val w1 = createFixture(withRequest = false)
        val b = w2.candidates[0]
        val bInW1 = addEmployee(w1.workplaceId, b.userId)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { accept(w2, 0) },
            second = {
                shiftService.reassignPublishedShift(w1.workplaceId, w1.scheduleId, w1.shiftId, reassignTo(bInW1), w1.managerUserId)
            },
        )

        // then: 수락 반영 뒤 재배정 진행 (수락은 아직 배정이 아님)
        assertThat(error).isNull()
        inTransaction {
            assertThat(findRequest(w2.requestId).status).isEqualTo(RequestStatus.ACCEPTED)
            assertThat(findCandidate(b.candidateId).status).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(findShift(w1.shiftId).member.id).isEqualTo(bInW1)
            assertThat(shiftAssignmentHistoryRepository.findAllByShift_IdOrderByCreateDateAsc(w1.shiftId)).hasSize(1)
        }

        // then: W2 승인은 W1 근무와 겹쳐 거부 → B의 공식 배정은 W1 하나
        assertBusinessError(SubstituteRequestErrorCode.CONFLICT_SHIFT) {
            substituteRequestService.approve(w2.requestId, w2.managerUserId)
        }
        inTransaction {
            assertThat(findRequest(w2.requestId).status).isEqualTo(RequestStatus.ACCEPTED)
            assertThat(findShift(w2.shiftId).member.user.id).isEqualTo(w2.requesterUserId)
        }
    }

    @Test
    @DisplayName("그 직원에게의 재배정이 Commit 전이면 다른 근무지 대타 수락이 기다렸다가 근무가 겹쳐 거부된다")
    fun otherWorkplaceAcceptWaitsForReassign() {
        // given
        val w2 = createFixture(withRequest = true)
        val w1 = createFixture(withRequest = false)
        val b = w2.candidates[0]
        val bInW1 = addEmployee(w1.workplaceId, b.userId)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = {
                shiftService.reassignPublishedShift(w1.workplaceId, w1.scheduleId, w1.shiftId, reassignTo(bInW1), w1.managerUserId)
            },
            second = { accept(w2, 0) },
        )

        // then: B의 공식 배정은 W1 하나, W2 수락은 남지 않음
        assertBusinessError(error, SubstituteRequestErrorCode.CONFLICT_SHIFT)
        inTransaction {
            assertThat(findShift(w1.shiftId).member.id).isEqualTo(bInW1)
            assertThat(findCandidate(b.candidateId).status).isEqualTo(CandidateStatus.PENDING)
            assertThat(findRequest(w2.requestId).status).isEqualTo(RequestStatus.OPEN)
        }
    }

    // ===================== 실행 헬퍼 =====================

    private fun accept(f: Fixture, index: Int) {
        substituteCandidateService.acceptCandidate(f.candidates[index].candidateId, f.candidates[index].userId)
    }

    private fun reassignTo(targetMemberId: Long): ShiftReassignRequest = ShiftReassignRequest(targetMemberId, emptyList())

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

    private fun findRequest(requestId: Long): SubstituteRequest =
        substituteRequestRepository.findById(requestId).orElseThrow()

    private fun findShift(shiftId: Long): Shift = shiftRepository.findById(shiftId).orElseThrow()

    private fun findCandidate(candidateId: Long): SubstituteCandidate =
        substituteCandidateRepository.findById(candidateId).orElseThrow()

    private fun candidateStatuses(f: Fixture): List<CandidateStatus> =
        f.candidates.map { findCandidate(it.candidateId).status }

    private fun activeRequestCount(shiftId: Long): Long = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            substituteRequestRepository.findAll()
                .count { it.shift.id == shiftId && it.status in setOf(RequestStatus.OPEN, RequestStatus.ACCEPTED) }
                .toLong()
        }
    )

    private fun assertClosedByShiftChange(requestId: Long) {
        val request = findRequest(requestId)
        assertThat(request.status).isEqualTo(RequestStatus.CLOSED)
        assertThat(request.closeReason).isEqualTo(RequestCloseReason.SHIFT_CHANGED)
        assertThat(request.closedAt).isEqualTo(LocalDateTime.now(testClock))
    }

    private fun assertBusinessError(expected: ErrorCode, action: () -> Unit) =
        assertBusinessError(capture(action), expected)

    private fun assertBusinessError(error: Throwable?, expected: ErrorCode) {
        assertThat(error)
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(expected)
    }

    // ===================== Fixture =====================

    private data class CandidateRef(
        val candidateId: Long,
        val memberId: Long,
        val userId: Long,
    )

    private data class Fixture(
        val workplaceId: Long,
        val scheduleId: Long,
        val shiftId: Long,
        val startAt: LocalDateTime,
        val managerUserId: Long,
        val requesterUserId: Long,
        val targetMemberId: Long,
        val targetUserId: Long,
        val requestId: Long,
        val candidates: List<CandidateRef>,
    )

    // 관리자 M, 요청자 A의 공개 근무 1개, 재배정 대상 D, 후보 B·C
    // withRequest = true면 A의 OPEN 요청 1건 + B·C 대기 후보
    private fun createFixture(withRequest: Boolean): Fixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = workplaceRepository.save(Workplace("근무 변경 연결 테스트 매장 $suffix", "SCI-$suffix"))
            val startAt = now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)
            val schedule = Schedule(workplace, startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                .also { it.publish(now()) }
                .let { scheduleRepository.save(it) }

            val manager = saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
            val requester = saveMember(workplace, "requester-$suffix", WorkplaceRole.EMPLOYEE)
            val target = saveMember(workplace, "target-$suffix", WorkplaceRole.EMPLOYEE)
            val candidateMembers = (0 until 2).map { saveMember(workplace, "candidate$it-$suffix", WorkplaceRole.EMPLOYEE) }
            val shift = shiftRepository.save(Shift(schedule, requester, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))

            val request = if (withRequest) substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN)) else null
            val candidates = if (request == null) emptyList() else candidateMembers.map { member ->
                val candidate = substituteCandidateRepository.save(SubstituteCandidate(request, member))
                CandidateRef(requireNotNull(candidate.id), requireNotNull(member.id), requireNotNull(member.user.id))
            }

            Fixture(
                workplaceId = requireNotNull(workplace.id),
                scheduleId = requireNotNull(schedule.id),
                shiftId = requireNotNull(shift.id),
                startAt = startAt,
                managerUserId = requireNotNull(manager.user.id),
                requesterUserId = requireNotNull(requester.user.id),
                targetMemberId = requireNotNull(target.id),
                targetUserId = requireNotNull(target.user.id),
                requestId = request?.id ?: -1L,
                candidates = candidates,
            )
        }
    )

    // 그 근무지에 이 User의 EMPLOYEE 소속을 추가 (두 근무지에 소속된 직원)
    private fun addEmployee(workplaceId: Long, userId: Long): Long = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val member = workplaceMemberRepository.save(
                WorkplaceMember(
                    workplaceRepository.findById(workplaceId).orElseThrow(),
                    userRepository.findById(userId).orElseThrow(),
                    WorkplaceRole.EMPLOYEE,
                    now().minusDays(30),
                )
            )
            member.id
        }
    )

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    private fun saveMember(workplace: Workplace, name: String, role: WorkplaceRole): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, now().minusDays(30)))
    }

    companion object {
        // second 작업이 이 시간 안에 끝나지 않으면 "Lock 대기 중"으로 판단
        private const val LOCK_WAIT_CHECK_MILLIS = 1_000L

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
