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
import com.merge.backend.domain.substitute.event.SubstituteAllCandidatesRejectedEvent
import com.merge.backend.domain.substitute.event.SubstituteCandidateIneligibleEvent
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
import org.springframework.jdbc.core.JdbcTemplate
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

// 퇴사 확정 Command(V2)가 부를 퇴사자 후보 처리 함수 (ML-3-P·P1·A, G03-D-15)
// V2 Command 대신 "소속 leftAt 기록 + 함수 호출"을 한 Transaction으로 흉내 낸다. 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
@RecordApplicationEvents
class SubstituteMemberLeaveTest {

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
    private lateinit var jdbcTemplate: JdbcTemplate

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
    @DisplayName("대기 후보 B가 퇴사하면 B는 INELIGIBLE이 되고 다른 대기 후보가 있으면 요청은 OPEN으로 남는다")
    fun pendingCandidateBecomesIneligible() {
        // given: 후보 B·C 대기
        val f = createFixture(candidateCount = 2)

        // when
        val result = leave(f.candidates[0])

        // then
        assertThat(result.closedRequests).isEmpty()
        assertThat(result.ineligibleCandidacies).isEqualTo(1)
        inTransaction {
            val b = findCandidate(f.candidates[0].candidateId)
            assertThat(b.status).isEqualTo(CandidateStatus.INELIGIBLE)
            assertThat(b.respondedAt).isNull()
            assertThat(findCandidate(f.candidates[1].candidateId).status).isEqualTo(CandidateStatus.PENDING)
            assertThat(findRequest(f.requestId).status).isEqualTo(RequestStatus.OPEN)
        }
    }

    @Test
    @DisplayName("B가 퇴사해 INELIGIBLE이 된 뒤 마지막 후보 C가 거절하면 CANDIDATE_INELIGIBLE로 종료되고 그 사유의 이벤트가 나간다")
    fun lastRejectAfterLeaveClosesAsCandidateIneligible() {
        // given
        val f = createFixture(candidateCount = 2)
        leave(f.candidates[0])

        // when
        substituteCandidateService.rejectCandidate(f.candidates[1].candidateId, f.candidates[1].userId)

        // then
        inTransaction { assertClosedAs(f.requestId, RequestCloseReason.CANDIDATE_INELIGIBLE) }
        val events = applicationEvents.stream(SubstituteCandidateIneligibleEvent::class.java).toList()
        assertThat(events).hasSize(1)
        assertThat(events[0].requestId).isEqualTo(f.requestId)
        assertThat(events[0].recipientMemberIds).containsExactlyInAnyOrder(f.requesterMemberId, f.managerMemberId)
        assertThat(applicationEvents.stream(SubstituteAllCandidatesRejectedEvent::class.java).count()).isZero()
    }

    @Test
    @DisplayName("유일한 대기 후보가 퇴사하면 응답 가능한 후보가 없어 요청이 CANDIDATE_INELIGIBLE로 종료되고 이벤트는 내지 않는다")
    fun onlyPendingCandidateLeavesClosesRequest() {
        // given
        val f = createFixture(candidateCount = 1)
        val before = substituteEventCount()

        // when
        val result = leave(f.candidates[0])

        // then
        assertThat(result.closedRequests).containsExactly(CandidateIneligibleClosedRequest(f.requestId, f.requesterMemberId))
        assertThat(result.ineligibleCandidacies).isEqualTo(1)
        inTransaction {
            assertThat(findCandidate(f.candidates[0].candidateId).status).isEqualTo(CandidateStatus.INELIGIBLE)
            assertClosedAs(f.requestId, RequestCloseReason.CANDIDATE_INELIGIBLE)
        }
        // 알림은 V2 퇴사 확정의 결과로 V5가 정한다 (#80과 같은 방식)
        assertThat(substituteEventCount()).isEqualTo(before)
    }

    @Test
    @DisplayName("이미 퇴사했는데 대기로 남은 후보(예전 데이터)는 응답 가능한 후보로 세지 않아 요청이 종료된다")
    fun leftPendingCandidateIsNotRespondable() {
        // given: B·C 대기, C의 소속은 이 기능 전에 이미 끝남 (INELIGIBLE 처리 없이 PENDING으로 남음)
        val f = createFixture(candidateCount = 2)
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now().minusDays(1), f.candidates[1].memberId)

        // when
        val result = leave(f.candidates[0])

        // then: 응답 가능한 후보 = 현재 소속의 PENDING (ML-3-P1) → 0명
        assertThat(result.closedRequests).containsExactly(CandidateIneligibleClosedRequest(f.requestId, f.requesterMemberId))
        inTransaction {
            assertClosedAs(f.requestId, RequestCloseReason.CANDIDATE_INELIGIBLE)
            assertThat(findCandidate(f.candidates[1].candidateId).status).isEqualTo(CandidateStatus.PENDING)
        }
    }

    @Test
    @DisplayName("수락한 B가 퇴사하면 요청은 CANDIDATE_INELIGIBLE로 종료되고 B의 수락 기록은 남으며 승인은 거부되고 요청자는 다시 요청할 수 있다")
    fun acceptedCandidateLeavesClosesRequest() {
        // given: B 수락, C 대기
        val f = createFixture(candidateCount = 2)
        substituteCandidateService.acceptCandidate(f.candidates[0].candidateId, f.candidates[0].userId)

        // when
        val result = leave(f.candidates[0])

        // then
        assertThat(result.closedRequests).containsExactly(CandidateIneligibleClosedRequest(f.requestId, f.requesterMemberId))
        assertThat(result.ineligibleCandidacies).isZero()
        inTransaction {
            assertClosedAs(f.requestId, RequestCloseReason.CANDIDATE_INELIGIBLE)
            assertThat(findCandidate(f.candidates[0].candidateId).status).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(findCandidate(f.candidates[1].candidateId).status).isEqualTo(CandidateStatus.PENDING)
        }
        assertBusinessError(SubstituteRequestErrorCode.INVALID_REQUEST) {
            substituteRequestService.approve(f.requestId, f.managerUserId)
        }

        // then: 요청자 재요청 성공, 퇴사한 B는 새 후보가 아님
        val recreated = substituteRequestService.create(f.shiftId, f.requesterUserId)
        assertThat(recreated.requestCreated).isTrue()
        inTransaction {
            val newCandidateMemberIds = substituteCandidateRepository.findAll()
                .filter { it.request.id == recreated.requestId }
                .map { it.member.id }
            assertThat(newCandidateMemberIds).doesNotContain(f.candidates[0].memberId).contains(f.candidates[1].memberId)
        }
    }

    @Test
    @DisplayName("거절·INELIGIBLE 후보가 섞인 요청에서 마지막 후보가 거절하면 CANDIDATE_INELIGIBLE로 종료된다")
    fun mixedRejectedAndIneligibleClosesAsCandidateIneligible() {
        // given: B 거절, C 퇴사(INELIGIBLE), D 대기
        val f = createFixture(candidateCount = 3)
        substituteCandidateService.rejectCandidate(f.candidates[0].candidateId, f.candidates[0].userId)
        leave(f.candidates[1])

        // when
        substituteCandidateService.rejectCandidate(f.candidates[2].candidateId, f.candidates[2].userId)

        // then
        inTransaction { assertClosedAs(f.requestId, RequestCloseReason.CANDIDATE_INELIGIBLE) }
    }

    @Test
    @DisplayName("B의 수락이 Commit 전이면 퇴사 처리가 기다렸다가 수락된 상태를 보고 요청을 CANDIDATE_INELIGIBLE로 종료한다")
    fun leaveWaitsForAcceptInProgress() {
        // given
        val f = createFixture(candidateCount = 2)
        var result: MemberLeaveCandidacyResult? = null

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { substituteCandidateService.acceptCandidate(f.candidates[0].candidateId, f.candidates[0].userId) },
            second = { result = leave(f.candidates[0]) },
        )

        // then
        assertThat(error).isNull()
        assertThat(result?.closedRequests).containsExactly(CandidateIneligibleClosedRequest(f.requestId, f.requesterMemberId))
        inTransaction {
            assertClosedAs(f.requestId, RequestCloseReason.CANDIDATE_INELIGIBLE)
            assertThat(findCandidate(f.candidates[0].candidateId).status).isEqualTo(CandidateStatus.ACCEPTED)
        }
    }

    @Test
    @DisplayName("퇴사 처리가 Commit 전이면 B의 수락이 기다렸다가 거부되고 B는 INELIGIBLE로 남는다")
    fun acceptWaitsForLeaveInProgress() {
        // given
        val f = createFixture(candidateCount = 2)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { leaveInCurrentTransaction(f.candidates[0]) },
            second = { substituteCandidateService.acceptCandidate(f.candidates[0].candidateId, f.candidates[0].userId) },
        )

        // then
        assertBusinessError(error, SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER)
        inTransaction {
            assertThat(findCandidate(f.candidates[0].candidateId).status).isEqualTo(CandidateStatus.INELIGIBLE)
            assertThat(findRequest(f.requestId).status).isEqualTo(RequestStatus.OPEN)
        }
    }

    @Test
    @DisplayName("퇴사 처리가 Commit 전이면 다른 후보의 마지막 거절이 기다렸다가 CANDIDATE_INELIGIBLE로 종료한다")
    fun lastRejectWaitsForLeaveInProgress() {
        // given: B·C 대기, B 퇴사 처리 중
        val f = createFixture(candidateCount = 2)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { leaveInCurrentTransaction(f.candidates[0]) },
            second = { substituteCandidateService.rejectCandidate(f.candidates[1].candidateId, f.candidates[1].userId) },
        )

        // then
        assertThat(error).isNull()
        inTransaction {
            assertClosedAs(f.requestId, RequestCloseReason.CANDIDATE_INELIGIBLE)
            assertThat(findCandidate(f.candidates[0].candidateId).status).isEqualTo(CandidateStatus.INELIGIBLE)
            assertThat(findCandidate(f.candidates[1].candidateId).status).isEqualTo(CandidateStatus.REJECTED)
        }
    }

    @Test
    @DisplayName("다른 후보의 거절이 Commit 전이면 퇴사 처리가 기다렸다가 응답 가능한 후보가 없음을 보고 CANDIDATE_INELIGIBLE로 종료한다")
    fun leaveWaitsForRejectInProgress() {
        // given: B·C 대기, C 거절 중
        val f = createFixture(candidateCount = 2)
        var result: MemberLeaveCandidacyResult? = null

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { substituteCandidateService.rejectCandidate(f.candidates[1].candidateId, f.candidates[1].userId) },
            second = { result = leave(f.candidates[0]) },
        )

        // then
        assertThat(error).isNull()
        assertThat(result?.closedRequests).containsExactly(CandidateIneligibleClosedRequest(f.requestId, f.requesterMemberId))
        inTransaction {
            assertClosedAs(f.requestId, RequestCloseReason.CANDIDATE_INELIGIBLE)
            assertThat(findCandidate(f.candidates[0].candidateId).status).isEqualTo(CandidateStatus.INELIGIBLE)
        }
    }

    @Test
    @DisplayName("Transaction 없이 부르면 거부하고 아무것도 바꾸지 않는다")
    fun requiresCallerTransaction() {
        // given
        val f = createFixture(candidateCount = 1)

        // when & then
        assertThatThrownBy { substituteCandidateService.closeCandidaciesByMemberLeave(f.candidates[0].memberId, now()) }
            .isInstanceOf(IllegalTransactionStateException::class.java)
        inTransaction {
            assertThat(findCandidate(f.candidates[0].candidateId).status).isEqualTo(CandidateStatus.PENDING)
            assertThat(findRequest(f.requestId).status).isEqualTo(RequestStatus.OPEN)
        }
    }

    @Test
    @DisplayName("이미 시작한 근무의 요청은 건드리지 않는다")
    fun leavesStartedShiftRequestsAsIs() {
        // given: 근무 시작 한 시간 뒤 퇴사
        val f = createFixture(candidateCount = 1, daysUntilShift = 1)
        testClock.setInstant(f.startAt.plusHours(1).atZone(testClock.zone).toInstant())

        // when
        val result = leave(f.candidates[0])

        // then
        assertThat(result.closedRequests).isEmpty()
        assertThat(result.ineligibleCandidacies).isZero()
        inTransaction {
            assertThat(findCandidate(f.candidates[0].candidateId).status).isEqualTo(CandidateStatus.PENDING)
            assertThat(findRequest(f.requestId).status).isEqualTo(RequestStatus.OPEN)
        }
    }

    @Test
    @DisplayName("같은 User의 다른 근무지 소속으로 걸린 후보는 건드리지 않는다")
    fun leavesOtherWorkplaceCandidacyAsIs() {
        // given: 같은 사람이 근무지 A·B 모두에서 후보
        val a = createFixture(candidateCount = 1)
        val b = createFixture(candidateCount = 1, candidateUser = a.candidates[0].userId)

        // when: 근무지 A 소속만 퇴사
        leave(a.candidates[0])

        // then
        inTransaction {
            assertThat(findCandidate(a.candidates[0].candidateId).status).isEqualTo(CandidateStatus.INELIGIBLE)
            assertThat(findCandidate(b.candidates[0].candidateId).status).isEqualTo(CandidateStatus.PENDING)
            assertThat(findRequest(b.requestId).status).isEqualTo(RequestStatus.OPEN)
        }
    }

    @Test
    @DisplayName("승인된 요청과 이미 종료된 요청은 그대로 둔다")
    fun leavesApprovedAndClosedRequestsAsIs() {
        // given: B가 수락해 승인된 요청 1건 + B가 대기 중이던 요청이 관리자 종료된 1건
        val approved = createFixture(candidateCount = 1)
        substituteCandidateService.acceptCandidate(approved.candidates[0].candidateId, approved.candidates[0].userId)
        substituteRequestService.approve(approved.requestId, approved.managerUserId)
        val closed = createFixture(candidateCount = 2, sameWorkplaceAs = approved, daysUntilShift = 8)
        substituteRequestService.close(closed.requestId, closed.managerUserId)

        // when
        val result = leave(approved.candidates[0])

        // then
        assertThat(result.closedRequests).isEmpty()
        inTransaction {
            assertThat(findRequest(approved.requestId).status).isEqualTo(RequestStatus.APPROVED)
            assertThat(findCandidate(approved.candidates[0].candidateId).status).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(findRequest(closed.requestId).closeReason).isEqualTo(RequestCloseReason.MANAGER_CLOSED)
            assertThat(findCandidate(closed.candidates[0].candidateId).status).isEqualTo(CandidateStatus.PENDING)
        }
    }

    @Test
    @DisplayName("후보로 걸린 요청이 여러 건이면 모두 처리하고 닫은 요청은 ID 오름차순으로 돌려준다")
    fun handlesAllCandidaciesInIdOrder() {
        // given: B가 유일한 후보인 요청 2건 + B와 C가 후보인 요청 1건
        val first = createFixture(candidateCount = 1)
        val second = createFixture(candidateCount = 1, sameWorkplaceAs = first, daysUntilShift = 8)
        val shared = createFixture(candidateCount = 2, sameWorkplaceAs = first, daysUntilShift = 9)

        // when
        val result = leave(first.candidates[0])

        // then
        assertThat(result.closedRequests.map { it.requestId }).containsExactly(first.requestId, second.requestId)
        assertThat(result.ineligibleCandidacies).isEqualTo(3)
        inTransaction { assertThat(findRequest(shared.requestId).status).isEqualTo(RequestStatus.OPEN) }
    }

    @Test
    @DisplayName("퇴사 Transaction이 Lock 전에 후보를 읽어 둔 뒤 그 후보가 수락했어도 최신 상태를 보고 수락 기록을 지키며 요청을 종료한다")
    fun usesLatestStatusEvenIfReadBeforeLock() {
        // given
        val f = createFixture(candidateCount = 2)
        val readBefore = CountDownLatch(1)
        val acceptedByOther = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(1)

        try {
            // when: 퇴사 Transaction이 후보·요청을 Lock 없이 먼저 읽음 → 그 사이 B가 수락(Commit) → 퇴사 처리
            val leaving: Future<MemberLeaveCandidacyResult> = executor.submit(
                Callable {
                    inTransactionResult {
                        assertThat(findCandidate(f.candidates[0].candidateId).status).isEqualTo(CandidateStatus.PENDING)
                        assertThat(findRequest(f.requestId).status).isEqualTo(RequestStatus.OPEN)
                        readBefore.countDown()
                        await(acceptedByOther)
                        leaveInCurrentTransaction(f.candidates[0])
                    }
                }
            )
            assertThat(readBefore.await(10, SECONDS)).isTrue()
            substituteCandidateService.acceptCandidate(f.candidates[0].candidateId, f.candidates[0].userId)
            acceptedByOther.countDown()
            val result = leaving.get(30, SECONDS)

            // then: ACCEPTED를 INELIGIBLE로 덮어쓰지 않음
            assertThat(result.closedRequests).containsExactly(CandidateIneligibleClosedRequest(f.requestId, f.requesterMemberId))
            assertThat(result.ineligibleCandidacies).isZero()
            inTransaction {
                assertClosedAs(f.requestId, RequestCloseReason.CANDIDATE_INELIGIBLE)
                assertThat(findCandidate(f.candidates[0].candidateId).status).isEqualTo(CandidateStatus.ACCEPTED)
            }
        } finally {
            acceptedByOther.countDown()
            executor.shutdownNow()
        }
    }

    // ===================== 실행 헬퍼 =====================

    // V2 퇴사 확정처럼 한 Transaction에서 소속 종료 + 대타 처리
    private fun leave(candidate: CandidateRef): MemberLeaveCandidacyResult =
        inTransactionResult { leaveInCurrentTransaction(candidate) }

    private fun leaveInCurrentTransaction(candidate: CandidateRef): MemberLeaveCandidacyResult {
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), candidate.memberId)
        return substituteCandidateService.closeCandidaciesByMemberLeave(candidate.memberId, now())
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

    private fun findCandidate(candidateId: Long): SubstituteCandidate =
        substituteCandidateRepository.findById(candidateId).orElseThrow()

    private fun assertClosedAs(requestId: Long, reason: RequestCloseReason) {
        val request = findRequest(requestId)
        assertThat(request.status).isEqualTo(RequestStatus.CLOSED)
        assertThat(request.closeReason).isEqualTo(reason)
        assertThat(request.closedAt).isEqualTo(now())
    }

    // 대타 도메인 이벤트 수 (com.merge.backend.domain.substitute.event)
    private fun substituteEventCount(): Long =
        applicationEvents.stream(Any::class.java)
            .filter { it.javaClass.packageName == SUBSTITUTE_EVENT_PACKAGE }
            .count()

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
        val shiftId: Long,
        val requestId: Long,
        val startAt: LocalDateTime,
        val requesterUserId: Long,
        val requesterMemberId: Long,
        val managerUserId: Long,
        val managerMemberId: Long,
        val candidates: List<CandidateRef>,
    )

    // 관리자 1명, 요청자 1명의 공개 근무, OPEN Request 1개 + 대기 후보 N명
    // sameWorkplaceAs: 그 근무지의 관리자·요청자·후보를 다시 씀 (요청자의 다른 근무, 앞 후보부터 다시 후보)
    // candidateUser: 첫 후보를 이 User의 새 소속으로 만듦 (다른 근무지 소속 확인용)
    private fun createFixture(
        candidateCount: Int,
        daysUntilShift: Long = 7,
        sameWorkplaceAs: Fixture? = null,
        candidateUser: Long? = null,
    ): Fixture = inTransactionResult {
        val suffix = uniqueSuffix()
        val startAt = now().plusDays(daysUntilShift).withHour(10).withMinute(0).withSecond(0).withNano(0)

        val workplace = sameWorkplaceAs?.let { workplaceRepository.findById(it.workplaceId).orElseThrow() }
            ?: workplaceRepository.save(Workplace("퇴사 후보 처리 테스트 매장 $suffix", "MLV-$suffix"))
        val schedule = savePublishedSchedule(workplace, startAt)

        val manager = sameWorkplaceAs?.let { workplaceMemberRepository.findById(it.managerMemberId).orElseThrow() }
            ?: saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
        val requester = sameWorkplaceAs?.let { workplaceMemberRepository.findById(it.requesterMemberId).orElseThrow() }
            ?: saveMember(workplace, "requester-$suffix", WorkplaceRole.EMPLOYEE)
        val shift = shiftRepository.save(Shift(schedule, requester, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))
        val request = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))

        val reused = sameWorkplaceAs?.candidates.orEmpty().map { workplaceMemberRepository.findById(it.memberId).orElseThrow() }
        val candidateMembers = (0 until candidateCount).map { i ->
            reused.getOrNull(i)
                ?: if (i == 0 && candidateUser != null) {
                    workplaceMemberRepository.save(
                        WorkplaceMember(workplace, userRepository.findById(candidateUser).orElseThrow(), WorkplaceRole.EMPLOYEE, now().minusDays(30))
                    )
                } else {
                    saveMember(workplace, "candidate$i-$suffix", WorkplaceRole.EMPLOYEE)
                }
        }
        val candidates = candidateMembers.map { member ->
            val candidate = substituteCandidateRepository.save(SubstituteCandidate(request, member))
            CandidateRef(requireNotNull(candidate.id), requireNotNull(member.id), requireNotNull(member.user.id))
        }

        Fixture(
            workplaceId = requireNotNull(workplace.id),
            shiftId = requireNotNull(shift.id),
            requestId = requireNotNull(request.id),
            startAt = startAt,
            requesterUserId = requireNotNull(requester.user.id),
            requesterMemberId = requireNotNull(requester.id),
            managerUserId = requireNotNull(manager.user.id),
            managerMemberId = requireNotNull(manager.id),
            candidates = candidates,
        )
    }

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    private fun savePublishedSchedule(workplace: Workplace, startAt: LocalDateTime): Schedule {
        val weekStart = startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // 같은 근무지·같은 주 근무표는 하나뿐 (Unique)
        return scheduleRepository.findByWorkplace_IdAndWeekStartDate(workplace.id, weekStart).orElse(null)
            ?: scheduleRepository.save(Schedule(workplace, weekStart).also { it.publish(now()) })
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
