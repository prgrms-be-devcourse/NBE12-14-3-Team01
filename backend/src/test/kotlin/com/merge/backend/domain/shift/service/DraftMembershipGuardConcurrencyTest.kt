package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.dto.ShiftRequest
import com.merge.backend.domain.shift.entity.RegularShiftPattern
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.RegularShiftPatternRepository
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.dto.request.LeaveConfirmRequest
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.domain.workplace.service.LeaveConfirmService
import com.merge.backend.domain.workplace.service.LeavePreviewService
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.ErrorCode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
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

// DRAFT 편집·Pattern 복사의 소속 Guard와 퇴사 확정의 경합 (ML-5 §5-1, ML5-T14·T15, ML-2-INV-02 "퇴사 후 퇴사자 DRAFT 0건")
// 퇴사 확정은 POST가 비활성이라 Service를 직접 부른다. 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class DraftMembershipGuardConcurrencyTest {

    @Autowired
    private lateinit var shiftService: ShiftService

    @Autowired
    private lateinit var scheduleService: ScheduleService

    @Autowired
    private lateinit var leaveConfirmService: LeaveConfirmService

    @Autowired
    private lateinit var leavePreviewService: LeavePreviewService

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var regularShiftPatternRepository: RegularShiftPatternRepository

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

    // ===================== DRAFT 근무 추가 =====================

    @Test
    @DisplayName("ML5-T14: 퇴사 확정이 대상 소속을 잡고 있으면 그 직원의 DRAFT 추가는 기다렸다가 SFT-004로 거부되고 퇴사자 DRAFT는 0건이다")
    fun draftAddWaitsForLeaveAndIsRejected() {
        // given
        val f = createFixture()
        val leave = leaveRequestOf(f)

        // when: 퇴사가 먼저 소속 FOR UPDATE를 잡은 채 Commit 전 → DRAFT 추가
        val error = runSecondWhileFirstHoldsLock(
            first = { leaveConfirmService.confirm(f.managerUserId, f.workplaceId, f.leaverMemberId, leave) },
            second = { shiftService.create(draftRequest(f.leaverMemberId), f.workplaceId, f.draftScheduleId, f.managerUserId) },
        )

        // then
        assertBusinessError(error, ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE)
        assertThat(leftAtOf(f.leaverMemberId)).isNotNull()
        assertThat(draftCountOf(f.leaverMemberId)).isZero()
    }

    @Test
    @DisplayName("ML5-T15: DRAFT 추가가 소속을 먼저 잡으면 퇴사 확정은 기다렸다가 새 DRAFT를 보고 WPL-010(STALE)으로 끝나며 퇴사는 반영되지 않는다")
    fun leaveWaitsForDraftAddAndBecomesStale() {
        // given: 미리보기를 먼저 받아 둠 (DRAFT 0건 기준)
        val f = createFixture()
        val leave = leaveRequestOf(f)

        // when: DRAFT 추가가 소속 FOR SHARE를 잡은 채 Commit 전 → 퇴사 확정
        val error = runSecondWhileFirstHoldsLock(
            first = { shiftService.create(draftRequest(f.leaverMemberId), f.workplaceId, f.draftScheduleId, f.managerUserId) },
            second = { leaveConfirmService.confirm(f.managerUserId, f.workplaceId, f.leaverMemberId, leave) },
        )

        // then
        assertBusinessError(error, WorkplaceErrorCode.STALE_LEAVE_PLAN)
        assertThat(leftAtOf(f.leaverMemberId)).isNull()
        assertThat(draftCountOf(f.leaverMemberId)).isEqualTo(1)
    }

    @Test
    @DisplayName("ML5-T14 (수정): 퇴사 확정이 소속을 잡고 있으면 다른 직원의 DRAFT를 퇴사자에게 옮기는 수정도 기다렸다가 SFT-004로 거부된다")
    fun draftModifyToLeaverWaitsForLeaveAndIsRejected() {
        // given: 다른 직원 C의 DRAFT 근무
        val f = createFixture()
        val shiftId = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                shiftRepository.save(
                    Shift(
                        scheduleRepository.findById(f.draftScheduleId).orElseThrow(),
                        workplaceMemberRepository.findById(f.otherMemberId).orElseThrow(),
                        DRAFT_START, DRAFT_START.plusHours(4), ShiftStatus.SCHEDULED,
                    ),
                ).id
            },
        )
        val leave = leaveRequestOf(f)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { leaveConfirmService.confirm(f.managerUserId, f.workplaceId, f.leaverMemberId, leave) },
            second = { shiftService.modify(f.workplaceId, f.draftScheduleId, shiftId, draftRequest(f.leaverMemberId), f.managerUserId) },
        )

        // then
        assertBusinessError(error, ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE)
        assertThat(draftCountOf(f.leaverMemberId)).isZero()
        assertThat(jdbcTemplate.queryForObject("SELECT member_id FROM shift WHERE id = ?", Long::class.java, shiftId))
            .isEqualTo(f.otherMemberId)
    }

    // ===================== 근무표 생성 (Pattern 복사) =====================

    @Test
    @DisplayName("ML5-T14 (Pattern): 퇴사 확정이 소속을 잡고 있으면 근무표 생성은 기다렸다가 퇴사자의 Pattern을 복사하지 않고 다른 직원 것만 만든다")
    fun scheduleCreationWaitsForLeaveAndSkipsLeaverPattern() {
        // given
        val f = createFixture(withPatterns = true)
        val leave = leaveRequestOf(f)
        var shiftCount = -1

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { leaveConfirmService.confirm(f.managerUserId, f.workplaceId, f.leaverMemberId, leave) },
            second = { shiftCount = scheduleService.createDraftSchedule(f.managerUserId, f.workplaceId, PATTERN_WEEK).shiftCount },
        )

        // then
        assertThat(error).isNull()
        assertThat(shiftCount).isEqualTo(1)
        assertThat(draftCountOf(f.leaverMemberId)).isZero()
        assertThat(draftCountOf(f.otherMemberId)).isEqualTo(1)
    }

    @Test
    @DisplayName("ML5-T15 (Pattern): 근무표 생성이 소속을 먼저 잡으면 퇴사 확정은 기다렸다가 새로 복사된 DRAFT를 보고 WPL-010(STALE)으로 끝난다")
    fun leaveWaitsForScheduleCreationAndBecomesStale() {
        // given
        val f = createFixture(withPatterns = true)
        val leave = leaveRequestOf(f)

        // when
        val error = runSecondWhileFirstHoldsLock(
            first = { scheduleService.createDraftSchedule(f.managerUserId, f.workplaceId, PATTERN_WEEK) },
            second = { leaveConfirmService.confirm(f.managerUserId, f.workplaceId, f.leaverMemberId, leave) },
        )

        // then
        assertBusinessError(error, WorkplaceErrorCode.STALE_LEAVE_PLAN)
        assertThat(leftAtOf(f.leaverMemberId)).isNull()
        assertThat(draftCountOf(f.leaverMemberId)).isEqualTo(1)
    }

    // ===================== 헬퍼 =====================

    // first를 Commit하지 않은 채 멈추고 second를 실행. second가 Lock을 기다리는지 확인한 뒤 first를 Commit
    // (SubstituteRequestConcurrencyTest·SubstituteShiftChangeIntegrationTest와 같은 방식)
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
                        check(releaseFirst.await(30, SECONDS)) { "대기 시간 초과" }
                    }
                },
            )

            assertThat(firstExecuted.await(10, SECONDS))
                .describedAs("first 작업이 Commit 전 상태로 대기해야 한다")
                .isTrue()

            val secondFuture: Future<Throwable?> = executor.submit(Callable { capture(second) })

            assertThatThrownBy { secondFuture.get(LOCK_WAIT_CHECK_MILLIS, MILLISECONDS) }
                .describedAs("first가 Commit하기 전에는 second가 소속 Lock을 기다려야 한다")
                .isInstanceOf(TimeoutException::class.java)

            releaseFirst.countDown()
            firstFuture.get(10, SECONDS)

            return secondFuture.get(10, SECONDS)
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    private fun leaveRequestOf(f: Fixture): LeaveConfirmRequest =
        LeaveConfirmRequest(
            leavePreviewService.getPreview(f.managerUserId, f.workplaceId, f.leaverMemberId).previewContext,
            emptyList(),
        )

    private fun draftRequest(memberId: Long) = ShiftRequest(memberId, DRAFT_START, DRAFT_START.plusHours(4), false)

    private fun leftAtOf(memberId: Long): LocalDateTime? =
        jdbcTemplate.queryForObject("SELECT left_at FROM workplace_member WHERE id = ?", LocalDateTime::class.java, memberId)

    private fun draftCountOf(memberId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM shift s JOIN schedule sc ON sc.id = s.schedule_id WHERE s.member_id = ? AND sc.status = 'DRAFT'",
            Long::class.java,
            memberId,
        ) ?: 0L

    private fun assertBusinessError(error: Throwable?, expected: ErrorCode) {
        assertThat(error)
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(expected)
    }

    private data class Fixture(
        val workplaceId: Long,
        val managerUserId: Long,
        val leaverMemberId: Long,
        val otherMemberId: Long,
        val draftScheduleId: Long,
    )

    // 관리자 M, 퇴사 대상 B(근무 없음), 다른 직원 C, DRAFT 근무표 1개. withPatterns면 B·C의 정기 근무 Pattern
    private fun createFixture(withPatterns: Boolean = false): Fixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val suffix = UUID.randomUUID().toString().substring(0, 8)
            val workplace = workplaceRepository.save(Workplace("소속 Guard 테스트 매장 $suffix", "MG-$suffix"))
            val manager = saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
            val leaver = saveMember(workplace, "leaver-$suffix", WorkplaceRole.EMPLOYEE)
            val other = saveMember(workplace, "other-$suffix", WorkplaceRole.EMPLOYEE)
            val draftSchedule = scheduleRepository.save(Schedule(workplace, DRAFT_WEEK))
            if (withPatterns) {
                regularShiftPatternRepository.save(RegularShiftPattern(leaver, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)))
                regularShiftPatternRepository.save(RegularShiftPattern(other, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)))
            }

            Fixture(
                workplaceId = requireNotNull(workplace.id),
                managerUserId = requireNotNull(manager.user.id),
                leaverMemberId = requireNotNull(leaver.id),
                otherMemberId = requireNotNull(other.id),
                draftScheduleId = requireNotNull(draftSchedule.id),
            )
        },
    )

    private fun saveMember(workplace: Workplace, name: String, role: WorkplaceRole): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, LocalDateTime.now(testClock).minusDays(30)))
    }

    companion object {
        // second 작업이 이 시간 안에 끝나지 않으면 "Lock 대기 중"으로 판단
        private const val LOCK_WAIT_CHECK_MILLIS = 1_000L

        // 2026-10-29 12:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-10-29T03:00:00Z")

        // DRAFT 근무표 주 (월요일)와 그 주의 근무, Pattern으로 새로 만들 근무표 주
        private val DRAFT_WEEK: LocalDate = LocalDate.of(2026, 11, 9)
        private val DRAFT_START: LocalDateTime = LocalDateTime.of(2026, 11, 10, 9, 0)
        private val PATTERN_WEEK: LocalDate = LocalDate.of(2026, 11, 16)

        private fun capture(task: () -> Unit): Throwable? =
            try {
                task()
                null
            } catch (throwable: Throwable) {
                throwable
            }
    }
}
