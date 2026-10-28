package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
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
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
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

// 대타 승인: 역할 먼저 검사(W안, API-CATALOG §5-5)와 Lock 순서 Shift → Request (ML-5 §5). 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class SubstituteApprovePermissionLockTest {

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

    @BeforeEach
    fun fixClock() {
        // 같은 Context를 쓰는 다른 테스트가 시간을 바꿨을 수 있으므로 매번 고정
        testClock.setInstant(FIXED_NOW)
    }

    @Test
    @DisplayName("어디서도 관리자가 아닌 사람은 요청 존재·상태와 관계없이 같은 403, 다른 근무지 관리자는 없는 ID와 같은 404를 받는다")
    fun outOfScopeApproveGetsSameResponseRegardlessOfRequest() {
        // given: 없는 ID, OPEN, ACCEPTED, CLOSED
        val f = createFixture()
        val requestIds = listOf(NOT_EXISTING_ID, f.openRequestId, f.acceptedRequestId, f.closedRequestId)

        // when
        val employee = requestIds.map { approveError(it, f.bystanderUserId) }
        val formerManager = requestIds.map { approveError(it, f.formerManagerUserId) }
        val otherManager = requestIds.map { approveError(it, f.otherManagerUserId) }

        // then
        assertThat(employee).containsOnly(WorkplaceErrorCode.MANAGER_REQUIRED)
        assertThat(formerManager).containsOnly(WorkplaceErrorCode.MANAGER_REQUIRED)
        assertThat(otherManager).containsOnly(SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND)
        assertUnchanged(f)
    }

    @Test
    @DisplayName("권한이 없는 승인은 관리자가 근무와 요청을 잠그고 있어도 기다리지 않고 바로 거절된다")
    fun outOfScopeApproveDoesNotWaitForLocks() {
        // given
        val f = createFixture()

        // when: 다른 Transaction이 Shift·Request를 잠근 채 Commit 전
        val errors = runWhileLocksHeld(
            hold = {
                shiftRepository.findByIdForUpdate(f.shiftId)
                substituteRequestRepository.findByIdForUpdate(f.acceptedRequestId)
            },
            actions = listOf(
                { substituteRequestService.approve(f.acceptedRequestId, f.bystanderUserId) },
                { substituteRequestService.approve(f.acceptedRequestId, f.otherManagerUserId) },
            ),
        )

        // then
        assertBusinessError(errors[0], WorkplaceErrorCode.MANAGER_REQUIRED)
        assertBusinessError(errors[1], SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND)
        assertUnchanged(f)
    }

    @Test
    @DisplayName("권한 확인 뒤 Lock을 기다리는 사이 다른 관리자가 요청을 종료하면 Lock 뒤 재검증에서 승인이 거절된다")
    fun approveRechecksStatusAfterLock() {
        // given
        val f = createFixture()

        // when: 다른 관리자의 종료가 Commit 전일 때 승인
        val approveError = runSecondWhileFirstHoldsLock(
            first = { substituteRequestService.close(f.acceptedRequestId, f.managerUserId) },
            second = { substituteRequestService.approve(f.acceptedRequestId, f.managerUserId) },
        )

        // then
        assertBusinessError(approveError, SubstituteRequestErrorCode.INVALID_REQUEST)
        inTransaction {
            assertThat(findRequest(f.acceptedRequestId).status).isEqualTo(RequestStatus.CLOSED)
            assertThat(shiftMemberId(f.shiftId)).isEqualTo(f.requesterMemberId)
        }
    }

    @Test
    @DisplayName("Shift → Request 순서로 잠그는 담당자 변경과 승인이 겹쳐도 Deadlock 없이 한쪽만 담당자를 바꾼다")
    fun approveAndShiftFirstHandoverDoNotDeadlock() {
        // given
        val f = createFixture()
        val shiftLocked = CountDownLatch(1)
        val lockRequestNow = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            // when: 담당자 변경(V3 재배정을 흉내)이 Shift를 잠근 뒤 멈춤
            val handover: Future<Throwable?> = executor.submit(
                Callable {
                    capture {
                        TransactionTemplate(transactionManager).executeWithoutResult {
                            val shift = shiftRepository.findByIdForUpdate(f.shiftId).orElseThrow()
                            shiftLocked.countDown()
                            await(lockRequestNow)
                            substituteRequestRepository.findByIdForUpdate(f.acceptedRequestId)
                            shift.changeMember(workplaceMemberRepository.findById(f.bystanderMemberId).orElseThrow())
                        }
                    }
                }
            )
            assertThat(shiftLocked.await(10, SECONDS)).isTrue()

            // 승인은 Shift Lock에서 기다려야 한다 (Request를 먼저 잡으면 아래에서 Deadlock)
            val approve: Future<Throwable?> = executor.submit(
                Callable { capture { substituteRequestService.approve(f.acceptedRequestId, f.managerUserId) } }
            )
            assertThatThrownBy { approve.get(LOCK_WAIT_CHECK_MILLIS, MILLISECONDS) }
                .describedAs("담당자 변경이 Commit하기 전에는 승인이 Shift Lock을 기다려야 한다")
                .isInstanceOf(TimeoutException::class.java)

            lockRequestNow.countDown()
            val handoverError = handover.get(30, SECONDS)
            val approveError = approve.get(30, SECONDS)

            // then: 담당자 변경이 먼저 끝나고, 승인은 바뀐 담당자를 보고 거절
            assertThat(handoverError).isNull()
            assertBusinessError(approveError, SubstituteRequestErrorCode.INVALID_MEMBER)
            inTransaction {
                assertThat(shiftMemberId(f.shiftId)).isEqualTo(f.bystanderMemberId)
                assertThat(findRequest(f.acceptedRequestId).status).isEqualTo(RequestStatus.ACCEPTED)
            }
        } finally {
            lockRequestNow.countDown()
            executor.shutdownNow()
        }
    }

    // ===================== 실행 헬퍼 =====================

    private fun approveError(requestId: Long, actorUserId: Long): ErrorCode? =
        (capture { substituteRequestService.approve(requestId, actorUserId) } as? BusinessException)?.errorCode

    /*
     * SubstituteRequestConcurrencyTest와 같은 방식
     * 1) first를 트랜잭션 안에서 실행하고, 커밋하지 않은 채(Lock을 쥔 채) 멈춘다.
     * 2) second를 실행한다. Lock이 있다면 second는 first 커밋 전까지 끝날 수 없다.
     * 3) first를 커밋시키고 second의 결과(예외, 성공 시 null)를 반환한다.
     */
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
                        await(releaseFirst) // 커밋하지 않고 대기
                    }
                }
            )

            assertThat(firstExecuted.await(10, SECONDS))
                .describedAs("first 작업이 커밋 전 상태로 대기해야 한다")
                .isTrue()

            val secondFuture: Future<Throwable?> = executor.submit(Callable { capture(second) })

            assertThatThrownBy { secondFuture.get(LOCK_WAIT_CHECK_MILLIS, MILLISECONDS) }
                .describedAs("first가 커밋하기 전에는 second가 Lock을 기다려야 한다")
                .isInstanceOf(TimeoutException::class.java)

            releaseFirst.countDown()
            firstFuture.get(10, SECONDS)

            return secondFuture.get(10, SECONDS)
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    // 위와 같은 방식이지만 반대로, hold가 Lock을 쥔 동안 actions가 기다리지 않고 끝나는지 확인한다
    private fun runWhileLocksHeld(hold: () -> Unit, actions: List<() -> Unit>): List<Throwable?> {
        val executor = Executors.newFixedThreadPool(2)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)

        try {
            val holdFuture: Future<*> = executor.submit(
                Runnable {
                    TransactionTemplate(transactionManager).executeWithoutResult {
                        hold()
                        held.countDown()
                        await(release) // 커밋하지 않고 대기
                    }
                }
            )

            assertThat(held.await(10, SECONDS))
                .describedAs("hold 작업이 커밋 전 상태로 대기해야 한다")
                .isTrue()

            val errors = actions.map { action ->
                val future: Future<Throwable?> = executor.submit(Callable { capture(action) })
                try {
                    future.get(LOCK_WAIT_CHECK_MILLIS, MILLISECONDS)
                } catch (e: TimeoutException) {
                    throw AssertionError("권한이 없는 승인이 Lock을 기다리면 안 된다", e)
                }
            }

            release.countDown()
            holdFuture.get(10, SECONDS)

            return errors
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    // ===================== 검증 헬퍼 =====================

    private fun inTransaction(assertion: () -> Unit) {
        TransactionTemplate(transactionManager).executeWithoutResult { assertion() }
    }

    private fun findRequest(requestId: Long): SubstituteRequest =
        substituteRequestRepository.findById(requestId).orElseThrow()

    private fun shiftMemberId(shiftId: Long): Long? =
        shiftRepository.findById(shiftId).orElseThrow().member.id

    // 거절된 승인은 요청 상태와 근무 담당자를 바꾸지 않는다
    private fun assertUnchanged(f: Fixture) {
        inTransaction {
            assertThat(findRequest(f.openRequestId).status).isEqualTo(RequestStatus.OPEN)
            assertThat(findRequest(f.acceptedRequestId).status).isEqualTo(RequestStatus.ACCEPTED)
            assertThat(findRequest(f.closedRequestId).status).isEqualTo(RequestStatus.CLOSED)
            assertThat(shiftMemberId(f.shiftId)).isEqualTo(f.requesterMemberId)
        }
    }

    // ===================== Fixture =====================

    private data class Fixture(
        val managerUserId: Long,
        val bystanderUserId: Long,
        val bystanderMemberId: Long,
        val formerManagerUserId: Long,
        val otherManagerUserId: Long,
        val requesterMemberId: Long,
        val shiftId: Long,
        val openRequestId: Long,
        val acceptedRequestId: Long,
        val closedRequestId: Long,
    )

    // 근무지 A: 관리자, 요청자, 수락 후보, 관계없는 직원, 퇴사한 전 관리자 / 근무지 B: 관리자
    // 요청자의 근무 3개에 각각 ACCEPTED(실제 수락 흐름), OPEN, CLOSED 요청
    private fun createFixture(): Fixture {
        val (fixture, candidateId, candidateUserId) = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val suffix = uniqueSuffix()
                val workplace = saveWorkplace("A-$suffix")
                val otherWorkplace = saveWorkplace("B-$suffix")
                val schedule = savePublishedSchedule(workplace)

                val manager = saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
                val requester = saveMember(workplace, "requester-$suffix", WorkplaceRole.EMPLOYEE)
                val candidate = saveMember(workplace, "candidate-$suffix", WorkplaceRole.EMPLOYEE)
                val bystander = saveMember(workplace, "bystander-$suffix", WorkplaceRole.EMPLOYEE)
                val formerManager = saveMember(
                    workplace, "former-manager-$suffix", WorkplaceRole.MANAGER, leftAt = now().minusDays(1),
                )
                val otherManager = saveMember(otherWorkplace, "other-manager-$suffix", WorkplaceRole.MANAGER)

                val shift = saveShift(schedule, requester, shiftStartAt())
                val acceptedRequest = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))
                val candidateRow = substituteCandidateRepository.save(SubstituteCandidate(acceptedRequest, candidate))

                val openRequest = substituteRequestRepository.save(
                    SubstituteRequest(saveShift(schedule, requester, shiftStartAt().plusHours(5)), requester, RequestStatus.OPEN)
                )
                val closedRequest = substituteRequestRepository.save(
                    SubstituteRequest(saveShift(schedule, requester, shiftStartAt().plusHours(10)), requester, RequestStatus.OPEN)
                )
                closedRequest.closeByManager(now())

                Triple(
                    Fixture(
                        managerUserId = requireNotNull(manager.user.id),
                        bystanderUserId = requireNotNull(bystander.user.id),
                        bystanderMemberId = requireNotNull(bystander.id),
                        formerManagerUserId = requireNotNull(formerManager.user.id),
                        otherManagerUserId = requireNotNull(otherManager.user.id),
                        requesterMemberId = requireNotNull(requester.id),
                        shiftId = requireNotNull(shift.id),
                        openRequestId = requireNotNull(openRequest.id),
                        acceptedRequestId = requireNotNull(acceptedRequest.id),
                        closedRequestId = requireNotNull(closedRequest.id),
                    ),
                    requireNotNull(candidateRow.id),
                    requireNotNull(candidate.user.id),
                )
            }
        )

        // 실제 수락 흐름으로 ACCEPTED를 만든다
        substituteCandidateService.acceptCandidate(candidateId, candidateUserId)

        return fixture
    }

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    // 일주일 뒤 10:00
    private fun shiftStartAt(): LocalDateTime =
        now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)

    private fun saveWorkplace(suffix: String): Workplace =
        workplaceRepository.save(Workplace("대타 승인 권한 테스트 매장 $suffix", "APR-$suffix"))

    private fun savePublishedSchedule(workplace: Workplace): Schedule {
        val schedule = Schedule(
            workplace,
            shiftStartAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        )
        schedule.publish(now())
        return scheduleRepository.save(schedule)
    }

    private fun saveMember(
        workplace: Workplace,
        name: String,
        role: WorkplaceRole,
        leftAt: LocalDateTime? = null,
    ): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, now().minusDays(30), leftAt))
    }

    private fun saveShift(schedule: Schedule, member: WorkplaceMember, startAt: LocalDateTime): Shift =
        shiftRepository.save(Shift(schedule, member, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))

    companion object {
        // second 작업이 이 시간 안에 끝나지 않으면 "Lock 대기 중"으로 판단
        private const val LOCK_WAIT_CHECK_MILLIS = 1_000L

        private const val NOT_EXISTING_ID = Long.MAX_VALUE

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

        private fun assertBusinessError(error: Throwable?, expected: ErrorCode) {
            assertThat(error)
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(expected)
        }
    }
}
