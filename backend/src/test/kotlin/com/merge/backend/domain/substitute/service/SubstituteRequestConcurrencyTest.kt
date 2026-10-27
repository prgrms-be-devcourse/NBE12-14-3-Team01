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
import com.merge.backend.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowingConsumer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
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

@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class SubstituteRequestConcurrencyTest {

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

    // =====================================================================
    // 1. 스레드 10개가 동시에 실행 (반복 실행으로 우연히 통과하는 경우를 줄인다)
    // =====================================================================

    @Nested
    @DisplayName("스레드 10개 동시 실행")
    inner class SimultaneousTest {

        @RepeatedTest(REPEAT_COUNT)
        @DisplayName("같은 Shift에 대타 요청을 동시에 생성해도 활성 요청은 하나만 생성된다")
        fun concurrentCreateAllowsOnlyOneActiveRequest() {

            // given
            val fixture = createShiftFixture()

            // when
            val results = runSimultaneously(
                List(THREAD_COUNT) {
                    { substituteRequestService.create(fixture.shiftId, fixture.requesterUserId) }
                }
            )

            // then
            assertOneSucceedsOthersFailWith(results, SubstituteRequestErrorCode.ACTIVE_REQUEST_EXISTS)

            assertThat(countActiveRequests(fixture)).isEqualTo(1)
        }

        @RepeatedTest(REPEAT_COUNT)
        @DisplayName("같은 Request의 Candidate 10명이 동시에 수락해도 한 명만 ACCEPTED 된다")
        fun concurrentAcceptAllowsOnlyOneCandidate() {

            // given
            val fixture = createRequestFixture(THREAD_COUNT)

            // when
            val results = runSimultaneously(
                List(THREAD_COUNT) { i ->
                    {
                        substituteCandidateService.acceptCandidate(
                            fixture.candidateIds[i],
                            fixture.candidateUserIds[i],
                        )
                    }
                }
            )

            // then
            assertOneSucceedsOthersFailWith(results, SubstituteRequestErrorCode.REQUEST_NOT_OPEN)

            inTransaction {
                assertThat(findRequest(fixture.requestId).status).isEqualTo(RequestStatus.ACCEPTED)

                assertThat(candidateStatuses(fixture.candidateIds))
                    .containsOnlyOnce(CandidateStatus.ACCEPTED)
                    .filteredOn { it != CandidateStatus.ACCEPTED }
                    .containsOnly(CandidateStatus.PENDING)
            }
        }

        @RepeatedTest(REPEAT_COUNT)
        @DisplayName("Candidate 10명이 동시에 모두 거절하면 Request는 반드시 CLOSED 된다")
        fun concurrentRejectClosesRequestWhenNoPendingCandidateRemains() {

            // given
            val fixture = createRequestFixture(THREAD_COUNT)

            // when
            val results = runSimultaneously(
                List(THREAD_COUNT) { i ->
                    {
                        substituteCandidateService.rejectCandidate(
                            fixture.candidateIds[i],
                            fixture.candidateUserIds[i],
                        )
                    }
                }
            )

            // then - 모두 성공
            assertThat(results).containsOnlyNulls()

            // then - Lock이 없으면 "서로 상대가 아직 PENDING"이라고 보고 Request가 OPEN으로 남는다
            inTransaction {
                val request = findRequest(fixture.requestId)

                assertThat(request.status).isEqualTo(RequestStatus.CLOSED)
                assertThat(request.closeReason).isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED)

                assertThat(candidateStatuses(fixture.candidateIds))
                    .containsOnly(CandidateStatus.REJECTED)
            }
        }

        @RepeatedTest(REPEAT_COUNT)
        @DisplayName("같은 User가 시간이 겹치는 서로 다른 Request 10개를 동시에 수락해도 하나만 ACCEPTED 된다")
        fun concurrentAcceptOnDifferentRequestsAllowsOnlyOneForSameUser() {

            // given
            val fixture = createSameUserFixture(THREAD_COUNT)

            // when
            val results = runSimultaneously(
                List(THREAD_COUNT) { i ->
                    {
                        substituteCandidateService.acceptCandidate(
                            fixture.candidateIds[i],
                            fixture.candidateUserId,
                        )
                    }
                }
            )

            // then
            assertOneSucceedsOthersFailWith(results, SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE)

            inTransaction {
                assertThat(fixture.requestIds)
                    .extracting<RequestStatus> { findRequest(it).status }
                    .containsOnlyOnce(RequestStatus.ACCEPTED)
                    .filteredOn { it != RequestStatus.ACCEPTED }
                    .containsOnly(RequestStatus.OPEN)

                assertThat(candidateStatuses(fixture.candidateIds))
                    .containsOnlyOnce(CandidateStatus.ACCEPTED)
            }
        }
    }

    // =====================================================================
    // 2. 실행 순서를 강제한 테스트
    //    first가 Lock을 쥔 채 커밋 전에 멈춘 상태에서 second를 실행한다.
    //    → second가 실제로 기다리는지 + first 커밋 후 최신 상태를 보고 판단하는지 확인
    // =====================================================================

    @Nested
    @DisplayName("실행 순서 강제 (first 커밋 전 second 실행)")
    inner class ForcedOrderTest {

        @Test
        @DisplayName(
            "Shift Lock - 먼저 생성 중인 요청이 커밋될 때까지 두 번째 생성은 대기하고, " +
                "이후 ACTIVE_REQUEST_EXISTS로 실패한다"
        )
        fun secondCreateWaitsForFirstCreate() {

            // given
            val fixture = createShiftFixture()

            // when
            val secondError = runSecondWhileFirstHoldsLock(
                first = { substituteRequestService.create(fixture.shiftId, fixture.requesterUserId) },
                second = { substituteRequestService.create(fixture.shiftId, fixture.requesterUserId) },
            )

            // then
            assertBusinessError(secondError, SubstituteRequestErrorCode.ACTIVE_REQUEST_EXISTS)
            assertThat(countActiveRequests(fixture)).isEqualTo(1)
        }

        @Test
        @DisplayName(
            "Request Lock - 첫 번째 후보의 수락이 커밋될 때까지 두 번째 후보는 대기하고, " +
                "이후 REQUEST_NOT_OPEN으로 실패한다"
        )
        fun secondAcceptWaitsForFirstAccept() {

            // given
            val fixture = createRequestFixture(2)

            // when
            val secondError = runSecondWhileFirstHoldsLock(
                first = {
                    substituteCandidateService.acceptCandidate(fixture.candidateIds[0], fixture.candidateUserIds[0])
                },
                second = {
                    substituteCandidateService.acceptCandidate(fixture.candidateIds[1], fixture.candidateUserIds[1])
                },
            )

            // then
            assertBusinessError(secondError, SubstituteRequestErrorCode.REQUEST_NOT_OPEN)

            inTransaction {
                assertThat(findRequest(fixture.requestId).status).isEqualTo(RequestStatus.ACCEPTED)

                assertThat(candidateStatuses(fixture.candidateIds))
                    .containsExactly(CandidateStatus.ACCEPTED, CandidateStatus.PENDING)
            }
        }

        @Test
        @DisplayName("Request Lock - 첫 번째 거절이 커밋된 뒤 두 번째(마지막) 거절이 실행되어 Request가 CLOSED 된다")
        fun lastRejectSeesCommittedFirstReject() {

            // given
            val fixture = createRequestFixture(2)

            // when
            val secondError = runSecondWhileFirstHoldsLock(
                first = {
                    substituteCandidateService.rejectCandidate(fixture.candidateIds[0], fixture.candidateUserIds[0])
                },
                second = {
                    substituteCandidateService.rejectCandidate(fixture.candidateIds[1], fixture.candidateUserIds[1])
                },
            )

            // then
            assertThat(secondError).isNull()

            inTransaction {
                val request = findRequest(fixture.requestId)

                assertThat(request.status).isEqualTo(RequestStatus.CLOSED)
                assertThat(request.closeReason).isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED)
            }
        }

        @Test
        @DisplayName(
            "User Lock - 같은 User가 다른 Request를 수락하는 중이면 대기하고, " +
                "이후 CONFLICT_ACTIVE_SUBSTITUTE로 실패한다"
        )
        fun sameUserSecondAcceptWaitsForFirstAccept() {

            // given - Request가 서로 다르므로 Request Lock으로는 막히지 않는다
            val fixture = createSameUserFixture(2)

            // when
            val secondError = runSecondWhileFirstHoldsLock(
                first = { substituteCandidateService.acceptCandidate(fixture.candidateIds[0], fixture.candidateUserId) },
                second = { substituteCandidateService.acceptCandidate(fixture.candidateIds[1], fixture.candidateUserId) },
            )

            // then
            assertBusinessError(secondError, SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE)

            inTransaction {
                assertThat(findRequest(fixture.requestIds[0]).status).isEqualTo(RequestStatus.ACCEPTED)
                assertThat(findRequest(fixture.requestIds[1]).status).isEqualTo(RequestStatus.OPEN)
            }
        }
    }

    // ===================== 실행 헬퍼 =====================

    // 모든 작업을 준비시킨 뒤 동시에 출발시키고, 각 작업의 예외(성공 시 null)를 모은다
    private fun runSimultaneously(tasks: List<() -> Unit>): List<Throwable?> {
        val executor = Executors.newFixedThreadPool(tasks.size)
        val ready = CountDownLatch(tasks.size)
        val start = CountDownLatch(1)

        try {
            val futures = tasks.map { task ->
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        capture(task)
                    }
                )
            }

            assertThat(ready.await(5, SECONDS)).isTrue()
            start.countDown()

            return futures.map { it.get(30, SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    /*
     * 1) first를 트랜잭션 안에서 실행하고, 커밋하지 않은 채(Lock을 쥔 채) 멈춘다.
     * 2) second를 실행한다. Lock이 있다면 second는 first 커밋 전까지 끝날 수 없다.
     * 3) first를 커밋시키고 second의 결과(예외, 성공 시 null)를 반환한다.
     *
     * Lock이 없다면 second가 first 커밋 전에 끝나 버리므로 2)에서 실패하고,
     * 우연히 늦게 끝나더라도 커밋 안 된 데이터를 기준으로 판단하므로 결과 검증에서 실패한다.
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

    // ===================== 검증 헬퍼 =====================

    private fun inTransaction(assertion: () -> Unit) {
        TransactionTemplate(transactionManager).executeWithoutResult { assertion() }
    }

    private fun findRequest(requestId: Long): SubstituteRequest =
        substituteRequestRepository.findById(requestId).orElseThrow()

    private fun candidateStatuses(candidateIds: List<Long>): List<CandidateStatus> =
        candidateIds
            .map { substituteCandidateRepository.findById(it).orElseThrow() }
            .map { it.status }

    private fun countActiveRequests(fixture: ShiftFixture): Long =
        requireNotNull(
            TransactionTemplate(transactionManager).execute {
                substituteRequestRepository
                    .findAllByRequesterMember_User_IdOrderByCreateDateDesc(fixture.requesterUserId)
                    .filter { it.shift.id == fixture.shiftId }
                    .count { it.status == RequestStatus.OPEN || it.status == RequestStatus.ACCEPTED }
                    .toLong()
            }
        )

    // ===================== Fixture =====================

    // 요청 전 Shift 1개 + 대타 가능한 후보 1명
    private fun createShiftFixture(): ShiftFixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = saveWorkplace(suffix)
            val schedule = savePublishedSchedule(workplace)

            val requester = saveMember(workplace, "requester-$suffix")
            saveMember(workplace, "candidate-$suffix")

            val shift = saveShift(schedule, requester, shiftStartAt())

            ShiftFixture(requesterUserId = requireNotNull(requester.user.id), shiftId = requireNotNull(shift.id))
        }
    )

    // OPEN Request 1개 + PENDING Candidate N명
    private fun createRequestFixture(candidateCount: Int): RequestFixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = saveWorkplace(suffix)
            val schedule = savePublishedSchedule(workplace)

            val requester = saveMember(workplace, "requester-$suffix")
            val shift = saveShift(schedule, requester, shiftStartAt())

            val request = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))

            val candidateIds = mutableListOf<Long>()
            val candidateUserIds = mutableListOf<Long>()

            repeat(candidateCount) { i ->
                val member = saveMember(workplace, "candidate$i-$suffix")

                val candidate = substituteCandidateRepository.save(SubstituteCandidate(request, member))

                candidateIds += requireNotNull(candidate.id)
                candidateUserIds += requireNotNull(member.user.id)
            }

            RequestFixture(requireNotNull(request.id), candidateIds, candidateUserIds)
        }
    )

    // 후보자 1명이 받은, 서로 시간이 겹치는 OPEN Request N개
    private fun createSameUserFixture(requestCount: Int): SameUserFixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = saveWorkplace(suffix)
            val schedule = savePublishedSchedule(workplace)

            val candidateMember = saveMember(workplace, "candidate-$suffix")

            val requestIds = mutableListOf<Long>()
            val candidateIds = mutableListOf<Long>()

            repeat(requestCount) { i ->
                val requester = saveMember(workplace, "requester$i-$suffix")

                // 10분씩 어긋나지만 모두 4시간짜리라 서로 겹친다
                val shift = saveShift(schedule, requester, shiftStartAt().plusMinutes(10L * i))

                val request = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))

                val candidate = substituteCandidateRepository.save(SubstituteCandidate(request, candidateMember))

                requestIds += requireNotNull(request.id)
                candidateIds += requireNotNull(candidate.id)
            }

            SameUserFixture(requestIds, candidateIds, requireNotNull(candidateMember.user.id))
        }
    )

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    // 일주일 뒤 10:00
    private fun shiftStartAt(): LocalDateTime =
        now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)

    private fun saveWorkplace(suffix: String): Workplace =
        workplaceRepository.save(Workplace("대타 동시성 테스트 매장 $suffix", "SUB-$suffix"))

    private fun savePublishedSchedule(workplace: Workplace): Schedule {
        val schedule = Schedule(
            workplace,
            shiftStartAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        )
        schedule.publish(now())
        return scheduleRepository.save(schedule)
    }

    private fun saveMember(workplace: Workplace, name: String): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(
            WorkplaceMember(workplace, user, WorkplaceRole.EMPLOYEE, now().minusDays(30))
        )
    }

    private fun saveShift(schedule: Schedule, member: WorkplaceMember, startAt: LocalDateTime): Shift =
        shiftRepository.save(Shift(schedule, member, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))

    private data class ShiftFixture(val requesterUserId: Long, val shiftId: Long)

    private data class RequestFixture(
        val requestId: Long,
        val candidateIds: List<Long>,
        val candidateUserIds: List<Long>,
    )

    private data class SameUserFixture(
        val requestIds: List<Long>,
        val candidateIds: List<Long>,
        val candidateUserId: Long,
    )

    companion object {
        private const val THREAD_COUNT = 10
        private const val REPEAT_COUNT = 5

        // second 작업이 이 시간 안에 끝나지 않으면 "Lock 대기 중"으로 판단
        private const val LOCK_WAIT_CHECK_MILLIS = 1_000L

        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")

        // @RepeatedTest로 여러 번 만들어도 email / inviteCode가 겹치지 않도록
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

        private fun assertOneSucceedsOthersFailWith(results: List<Throwable?>, expected: ErrorCode) {
            assertThat(results)
                .describedAs("정확히 하나만 성공해야 한다")
                .filteredOn { it == null }
                .hasSize(1)

            assertThat(results)
                .filteredOn { it != null }
                .allSatisfy(ThrowingConsumer { error -> assertBusinessError(error, expected) })
        }

        private fun assertBusinessError(error: Throwable?, expected: ErrorCode) {
            assertThat(error)
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(expected)
        }
    }
}
