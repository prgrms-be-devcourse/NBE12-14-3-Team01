package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
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

// 대타 생성·수락·승인의 소속 Guard (ML-5 §4-4·§5, H6-C5·C7, ML5-R1·T5·T6·T8)
// 퇴사는 "소속 Row를 FOR UPDATE로 잡고 leftAt 기록"으로 흉내 낸다. 실제 V2 퇴사 확정은 Request를 먼저 잠그지만,
// 여기서는 소속 Lock만으로 직렬화되는지(Guard 자체)를 본다. 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class SubstituteMembershipGuardTest {

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

    @BeforeEach
    fun fixClock() {
        // 같은 Context를 쓰는 다른 테스트가 시간을 바꿨을 수 있으므로 매번 고정
        testClock.setInstant(FIXED_NOW)
    }

    // ===================== 퇴사 × 대타 생성 (H6-C7) =====================

    @Test
    @DisplayName("후보가 퇴사 중이면 대타 생성이 소속 Lock을 기다렸다가 퇴사한 후보는 빼고 요청을 만든다")
    fun createWaitsForCandidateLeaveAndSkipsLeftCandidate() {
        // given: 요청자 A, 후보 B·C
        val f = createFixture()

        // when: B 퇴사가 Commit 전일 때 대타 생성 (후보 선정은 B를 현재 소속으로 읽은 뒤 Lock을 기다림, ML5-R1 반례 A)
        var requestId: Long? = null
        val error = runSecondWhileFirstHoldsLock(
            first = { leaveHoldingOnlyMembership(f.candidateB.memberId) },
            second = { requestId = substituteRequestService.create(f.shiftId, f.requesterUserId).requestId },
        )

        // then: 퇴사한 B는 후보가 아니고 C만 후보
        assertThat(error).isNull()
        inTransaction {
            val id = requireNotNull(requestId)
            assertThat(substituteCandidateRepository.findByRequestIdAndMemberId(id, f.candidateB.memberId)).isNull()
            assertThat(substituteCandidateRepository.findByRequestIdAndMemberId(id, f.candidateC.memberId)?.status)
                .isEqualTo(CandidateStatus.PENDING)
        }
    }

    @Test
    @DisplayName("대타 생성이 후보 소속을 잡고 있으면 퇴사가 기다렸다가 새로 생긴 후보를 INELIGIBLE로 정리한다 (V4 정리 함수 기준)")
    fun leaveWaitsForCreateInProgress() {
        // given
        val f = createFixture()

        // when: 대타 생성이 B 소속 FOR SHARE를 잡은 채 Commit 전 → B 퇴사 (ML5-T6)
        val error = runSecondWhileFirstHoldsLock(
            first = { substituteRequestService.create(f.shiftId, f.requesterUserId) },
            second = { inTransactionResult { leaveWithCandidacies(f.candidateB.memberId) } },
        )

        // then: 퇴사는 생성 Commit 뒤에 진행돼 새 요청의 B를 찾아 INELIGIBLE로 정리 (V2 실제 경로는 다시 조회 → 409, V2 Test 범위)
        assertThat(error).isNull()
        inTransaction {
            val request = substituteRequestRepository.findAll().single { it.shift.id == f.shiftId }
            assertThat(request.status).isEqualTo(RequestStatus.OPEN)
            val requestId = requireNotNull(request.id)
            assertThat(substituteCandidateRepository.findByRequestIdAndMemberId(requestId, f.candidateB.memberId)?.status)
                .isEqualTo(CandidateStatus.INELIGIBLE)
            assertThat(substituteCandidateRepository.findByRequestIdAndMemberId(requestId, f.candidateC.memberId)?.status)
                .isEqualTo(CandidateStatus.PENDING)
        }
    }

    @Test
    @DisplayName("요청자가 퇴사 중이면 대타 생성이 소속 Lock을 기다렸다가 NOT_OWN_SHIFT로 거부되고 요청은 0건이다")
    fun createWaitsForRequesterLeave() {
        // given
        val f = createFixture()

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { leaveHoldingOnlyMembership(f.requesterMemberId) },
            second = { substituteRequestService.create(f.shiftId, f.requesterUserId) },
        )

        // then
        assertBusinessError(error, SubstituteRequestErrorCode.NOT_OWN_SHIFT)
        inTransaction {
            assertThat(substituteRequestRepository.findAll().filter { it.shift.id == f.shiftId }).isEmpty()
        }
    }

    // ===================== 퇴사 × 수락 (H6-C5, ML5-T5) =====================

    @Test
    @DisplayName("후보가 퇴사 중이면 수락이 소속 Lock을 기다렸다가 SUB-021로 거부되고 요청은 OPEN이다")
    fun acceptWaitsForLeaveOfCandidate() {
        // given: B·C 대기 후보인 OPEN 요청
        val f = createFixture()
        val requestId = requireNotNull(substituteRequestService.create(f.shiftId, f.requesterUserId).requestId)
        val candidateId = candidateIdOf(requestId, f.candidateB.memberId)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { leaveHoldingOnlyMembership(f.candidateB.memberId) },
            second = { substituteCandidateService.acceptCandidate(candidateId, f.candidateB.userId) },
        )

        // then
        assertBusinessError(error, SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER)
        inTransaction {
            assertThat(substituteRequestRepository.findById(requestId).orElseThrow().status).isEqualTo(RequestStatus.OPEN)
            assertThat(substituteCandidateRepository.findById(candidateId).orElseThrow().status)
                .isEqualTo(CandidateStatus.PENDING)
        }
    }

    // ===================== 퇴사 × 승인 (ML5-T8) =====================

    @Test
    @DisplayName("수락자가 퇴사 중이면 승인이 소속 Lock을 기다렸다가 SFT-004로 거부되고 근무 담당은 그대로다")
    fun approveWaitsForLeaveOfAccepter() {
        // given: B가 수락한 요청
        val f = createFixture()
        val requestId = requireNotNull(substituteRequestService.create(f.shiftId, f.requesterUserId).requestId)
        substituteCandidateService.acceptCandidate(candidateIdOf(requestId, f.candidateB.memberId), f.candidateB.userId)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { leaveHoldingOnlyMembership(f.candidateB.memberId) },
            second = { substituteRequestService.approve(requestId, f.managerUserId) },
        )

        // then: 퇴사자가 담당이 되지 않음
        assertBusinessError(error, ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE)
        inTransaction {
            assertThat(substituteRequestRepository.findById(requestId).orElseThrow().status).isEqualTo(RequestStatus.ACCEPTED)
            assertThat(shiftRepository.findById(f.shiftId).orElseThrow().member.id).isEqualTo(f.requesterMemberId)
        }
    }

    // ===================== 실행 헬퍼 =====================

    // 퇴사 중: 소속 Row만 잠그고 leftAt 기록 (Request를 잠그지 않아 소속 Guard만으로 직렬화되는지 확인)
    private fun leaveHoldingOnlyMembership(memberId: Long) {
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), memberId)
    }

    // 퇴사: leftAt 기록 + 후보로 걸린 요청 정리 (#92 SubstituteMemberLeaveTest와 같은 흉내)
    private fun leaveWithCandidacies(memberId: Long): MemberLeaveCandidacyResult {
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), memberId)
        return substituteCandidateService.closeCandidaciesByMemberLeave(memberId, now())
    }

    private fun candidateIdOf(requestId: Long, memberId: Long): Long = inTransactionResult {
        requireNotNull(requireNotNull(substituteCandidateRepository.findByRequestIdAndMemberId(requestId, memberId)).id)
    }

    // first를 Commit하지 않은 채로 멈추고 second를 실행. second가 Lock을 기다리는지 확인한 뒤 first를 Commit
    // (SubstituteMemberLeaveTest와 같은 방식)
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

    private fun assertBusinessError(error: Throwable?, expected: ErrorCode) {
        assertThat(error)
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(expected)
    }

    // ===================== Fixture =====================

    private data class MemberRef(
        val memberId: Long,
        val userId: Long,
    )

    private data class Fixture(
        val shiftId: Long,
        val managerUserId: Long,
        val requesterUserId: Long,
        val requesterMemberId: Long,
        val candidateB: MemberRef,
        val candidateC: MemberRef,
    )

    // 관리자 1명, 요청자 A의 공개 근무 1개(일주일 뒤), 후보가 될 직원 B·C (요청은 아직 없음)
    private fun createFixture(): Fixture = inTransactionResult {
        val suffix = uniqueSuffix()
        val startAt = now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)

        val workplace = workplaceRepository.save(Workplace("소속 Guard 테스트 매장 $suffix", "MSG-$suffix"))
        val schedule = savePublishedSchedule(workplace, startAt)

        val manager = saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
        val requester = saveMember(workplace, "requester-$suffix", WorkplaceRole.EMPLOYEE)
        val b = saveMember(workplace, "candidate-b-$suffix", WorkplaceRole.EMPLOYEE)
        val c = saveMember(workplace, "candidate-c-$suffix", WorkplaceRole.EMPLOYEE)
        val shift = shiftRepository.save(Shift(schedule, requester, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))

        Fixture(
            shiftId = requireNotNull(shift.id),
            managerUserId = requireNotNull(manager.user.id),
            requesterUserId = requireNotNull(requester.user.id),
            requesterMemberId = requireNotNull(requester.id),
            candidateB = MemberRef(requireNotNull(b.id), requireNotNull(b.user.id)),
            candidateC = MemberRef(requireNotNull(c.id), requireNotNull(c.user.id)),
        )
    }

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    private fun savePublishedSchedule(workplace: Workplace, startAt: LocalDateTime): Schedule {
        val weekStart = startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return scheduleRepository.save(Schedule(workplace, weekStart).also { it.publish(now()) })
    }

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
