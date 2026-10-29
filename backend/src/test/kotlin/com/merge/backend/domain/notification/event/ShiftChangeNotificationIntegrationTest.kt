package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.notification.service.ShiftChangeNotificationPlanner
import com.merge.backend.domain.shift.dto.PublishedShiftCreateRequest
import com.merge.backend.domain.shift.dto.ShiftReassignRequest
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftAssignmentReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftAssignmentHistoryRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.service.ShiftService
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.substitute.service.SubstituteCandidateService
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.domain.workplace.service.WorkplaceService
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
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

// 새 알림 6종을 실제 Command로 발생시키고 Commit 후 저장된 알림 Row를 MySQL에서 확인 (NOTIFICATION-TYPES §3, §5)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class ShiftChangeNotificationIntegrationTest {

    @Autowired
    private lateinit var shiftService: ShiftService

    @Autowired
    private lateinit var substituteCandidateService: SubstituteCandidateService

    @Autowired
    private lateinit var workplaceService: WorkplaceService

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

    // 이 값보다 큰 ID의 알림만 본다. given 단계의 기존 알림(예: 수락 시 SUBSTITUTE_ACCEPTED)을 빼기 위함
    private var since = 0L

    @BeforeEach
    fun fixClock() {
        // 같은 Context를 쓰는 다른 테스트가 시간을 바꿨을 수 있으므로 매번 고정
        testClock.setInstant(FIXED_NOW)
        since = 0L
    }

    @Test
    @DisplayName("NT-T10: A→D 재배정, B가 수락한 A의 요청 → A 합친 1건, B·D·다른 관리자 각 1건, 실행 관리자·대기 후보 0건, DB 기록은 각각 남는다")
    fun reassignNotifiesEachRoleOnce() {
        // given: A 요청, B 수락, C 대기
        val f = createFixture(withRequest = true)
        substituteCandidateService.acceptCandidate(f.candidateB.candidateId, f.candidateB.userId)

        // when
        markNotifications()
        shiftService.reassignPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, ShiftReassignRequest(f.target.memberId, emptyList()), f.manager.userId)

        // then
        val label = f.label
        assertThat(notificationsOf(f.requester)).containsExactly(
            NotificationType.SHIFT_REASSIGNED_AWAY to "$label 근무가 다른 직원에게 넘어갔습니다. 진행 중이던 대타 요청도 종료되었습니다.",
        )
        assertThat(notificationsOf(f.target)).containsExactly(NotificationType.SHIFT_ASSIGNED to "$label 근무가 배정되었습니다.")
        assertThat(notificationsOf(f.candidateB)).containsExactly(
            NotificationType.SUBSTITUTE_SHIFT_CHANGED to "$label 근무의 담당자가 바뀌어 대타 요청이 종료되었습니다.",
        )
        assertThat(notificationsOf(f.otherManager)).containsExactly(
            NotificationType.SUBSTITUTE_SHIFT_CHANGED to "$label 근무의 담당자가 바뀌어 대타 요청이 종료되었습니다.",
        )
        assertThat(notificationsOf(f.manager)).isEmpty() // NT-T14 실행 관리자
        assertThat(notificationsOf(f.candidateC)).isEmpty() // NT-T13 대기 후보
        assertThat(typesInWorkplace(f)).doesNotContain(NotificationType.SUBSTITUTE_MANAGER_CLOSED.name) // NT-T5

        // DB는 합치지 않는다: 배정 이력, SHIFT_CHANGED 종료, 후보 ACCEPTED가 각각 남음
        inTransaction {
            assertThat(shiftAssignmentHistoryRepository.findAllByShift_IdOrderByCreateDateAsc(f.shiftId).map { it.reason })
                .containsExactly(ShiftAssignmentReason.MANAGER_REASSIGNED)
            assertThat(substituteRequestRepository.findById(f.requestId).orElseThrow().closeReason).isEqualTo(RequestCloseReason.SHIFT_CHANGED)
            assertThat(substituteCandidateRepository.findById(f.candidateB.candidateId).orElseThrow().status).isEqualTo(CandidateStatus.ACCEPTED)
        }
    }

    @Test
    @DisplayName("취소: A 합친 SHIFT_CANCELLED 1건, 수락자·다른 관리자 SUBSTITUTE_SHIFT_CHANGED, 실행 관리자 0건")
    fun cancelNotifiesOwnerAccepterAndOtherManagers() {
        // given
        val f = createFixture(withRequest = true)
        substituteCandidateService.acceptCandidate(f.candidateB.candidateId, f.candidateB.userId)

        // when
        markNotifications()
        shiftService.cancelPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, f.manager.userId)

        // then
        val label = f.label
        assertThat(notificationsOf(f.requester)).containsExactly(
            NotificationType.SHIFT_CANCELLED to "$label 근무가 취소되었습니다. 진행 중이던 대타 요청도 종료되었습니다.",
        )
        assertThat(notificationsOf(f.candidateB)).containsExactly(
            NotificationType.SUBSTITUTE_SHIFT_CHANGED to "$label 근무가 취소되어 대타 요청이 종료되었습니다.",
        )
        assertThat(notificationsOf(f.otherManager).map { it.first }).containsExactly(NotificationType.SUBSTITUTE_SHIFT_CHANGED)
        assertThat(notificationsOf(f.manager)).isEmpty()
        assertThat(notificationsOf(f.candidateC)).isEmpty()
    }

    @Test
    @DisplayName("활성 대타 요청이 없는 근무 취소: 원래 담당에게 SHIFT_CANCELLED만, 관리자 알림 0건 (NT-T16 ②)")
    fun cancelWithoutRequestNotifiesOwnerOnly() {
        val f = createFixture(withRequest = false)

        shiftService.cancelPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, f.manager.userId)

        assertThat(notificationsOf(f.requester)).containsExactly(NotificationType.SHIFT_CANCELLED to "${f.label} 근무가 취소되었습니다.")
        assertThat(notificationsOf(f.otherManager)).isEmpty()
        assertThat(notificationsOf(f.manager)).isEmpty()
    }

    @Test
    @DisplayName("수락했던 후보에게 직접 재배정하면 그 사람은 SHIFT_ASSIGNED 1건에 '수락했던 대타 요청은 종료' 문구를 받는다")
    fun reassignToAccepterMergesIntoAssigned() {
        val f = createFixture(withRequest = true)
        substituteCandidateService.acceptCandidate(f.candidateB.candidateId, f.candidateB.userId)
        markNotifications()

        shiftService.reassignPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, ShiftReassignRequest(f.candidateB.memberId, emptyList()), f.manager.userId)

        assertThat(notificationsOf(f.candidateB)).containsExactly(
            NotificationType.SHIFT_ASSIGNED to "${f.label} 근무가 배정되었습니다. 수락했던 대타 요청은 종료되었습니다.",
        )
    }

    @Test
    @DisplayName("공개 근무표에 근무를 추가하면 새 담당에게 SHIFT_ASSIGNED 1건 (API-PS-01)")
    fun addPublishedShiftNotifiesAssignee() {
        val f = createFixture(withRequest = false)
        val startAt = f.startAt.plusHours(6)

        val shift = shiftService.createPublishedShift(
            f.workplaceId, f.scheduleId, PublishedShiftCreateRequest(f.target.memberId, startAt, startAt.plusHours(3), emptyList()), f.manager.userId,
        )

        assertThat(notificationsOf(f.target)).containsExactly(
            NotificationType.SHIFT_ASSIGNED to "${ShiftChangeNotificationPlanner.shiftLabel(shift.startAt, shift.endAt)} 근무가 배정되었습니다.",
        )
        assertThat(notificationsOf(f.manager)).isEmpty()
    }

    @Test
    @DisplayName("이미 소속이 끝난 담당의 근무를 취소해도 그 사람에게 알림을 만들지 않는다 (R1, NT-T2)")
    fun leftOwnerGetsNoNotification() {
        val f = createFixture(withRequest = false)
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now().minusDays(1), f.requester.memberId)

        shiftService.cancelPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, f.manager.userId)

        assertThat(notificationsOf(f.requester)).isEmpty()
    }

    @Test
    @DisplayName("거절로 응답 가능한 후보가 없어지면 요청자·현재 관리자에게 SUBSTITUTE_CANDIDATE_INELIGIBLE (요청자 문구는 다시 요청 안내), 퇴사한 후보는 0건")
    fun candidateIneligibleNotifiesRequesterAndManagers() {
        // given: B·C 대기, C 소속만 끝남 → B 거절 (#92)
        val f = createFixture(withRequest = true)
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now().minusHours(1), f.candidateC.memberId)

        // when
        markNotifications()
        substituteCandidateService.rejectCandidate(f.candidateB.candidateId, f.candidateB.userId)

        // then
        val base = "${f.label} 근무의 대타 요청이 응답할 수 있는 후보가 없어 종료되었습니다."
        assertThat(notificationsOf(f.requester)).containsExactly(
            NotificationType.SUBSTITUTE_CANDIDATE_INELIGIBLE to "$base 근무는 그대로이니 필요하면 다시 요청해 주세요.",
        )
        assertThat(notificationsOf(f.manager)).containsExactly(NotificationType.SUBSTITUTE_CANDIDATE_INELIGIBLE to base)
        assertThat(notificationsOf(f.otherManager)).containsExactly(NotificationType.SUBSTITUTE_CANDIDATE_INELIGIBLE to base)
        assertThat(notificationsOf(f.candidateB)).isEmpty()
        assertThat(notificationsOf(f.candidateC)).isEmpty()
    }

    @Test
    @DisplayName("NT-T6: 가입하면 그 근무지 현재 관리자에게만 '가입' 1건, 퇴사 후 재가입하면 '다시 가입' 1건")
    fun joinAndRejoinNotifyCurrentManagers() {
        // given
        val f = createFixture(withRequest = false)
        val inviteCode = workplaceRepository.findById(f.workplaceId).orElseThrow().inviteCode
        val newcomer = userRepository.save(User("newcomer-${uniqueSuffix()}@test.com", "password-hash", "새직원"))

        // when: 처음 가입
        val first = workplaceService.joinWorkplace(inviteCode, newcomer)

        // then
        assertThat(notificationsOf(f.manager)).containsExactly(NotificationType.WORKPLACE_MEMBER_JOINED to "새직원님이 근무지에 가입했습니다.")
        assertThat(notificationsOf(f.otherManager)).containsExactly(NotificationType.WORKPLACE_MEMBER_JOINED to "새직원님이 근무지에 가입했습니다.")
        assertThat(notificationsOf(f.requester)).isEmpty()

        // when: 퇴사 후 재가입
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), first.id)
        workplaceService.joinWorkplace(inviteCode, newcomer)

        // then
        assertThat(notificationsOf(f.manager).map { it.second }).containsExactly(
            "새직원님이 근무지에 가입했습니다.",
            "새직원님이 근무지에 다시 가입했습니다.",
        )
    }

    // ===================== 헬퍼 =====================

    private fun notificationsOf(member: MemberRef): List<Pair<NotificationType, String>> =
        jdbcTemplate.queryForList(
            "SELECT type, message FROM notification WHERE recipient_member_id = ? AND id > ? ORDER BY id",
            member.memberId,
            since,
        ).map { NotificationType.valueOf(it["type"] as String) to it["message"] as String }

    private fun typesInWorkplace(f: Fixture): List<String?> =
        jdbcTemplate.queryForList(
            "SELECT n.type FROM notification n JOIN workplace_member m ON m.id = n.recipient_member_id WHERE m.workplace_id = ? AND n.id > ?",
            String::class.java,
            f.workplaceId,
            since,
        )

    private fun markNotifications() {
        since = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM notification", Long::class.java) ?: 0L
    }

    private fun inTransaction(assertion: () -> Unit) {
        TransactionTemplate(transactionManager).executeWithoutResult { assertion() }
    }

    private data class MemberRef(val memberId: Long, val userId: Long)

    private data class CandidateRef(val candidateId: Long, val memberId: Long, val userId: Long)

    private fun notificationsOf(candidate: CandidateRef) = notificationsOf(MemberRef(candidate.memberId, candidate.userId))

    private data class Fixture(
        val workplaceId: Long,
        val scheduleId: Long,
        val shiftId: Long,
        val startAt: LocalDateTime,
        val label: String,
        val manager: MemberRef,
        val otherManager: MemberRef,
        val requester: MemberRef,
        val target: MemberRef,
        val requestId: Long,
        val candidateB: CandidateRef,
        val candidateC: CandidateRef,
    )

    // 관리자 M(실행)·M2, 요청자 A의 공개 근무 1개, 재배정 대상 D. withRequest면 A의 OPEN 요청 + B·C 대기 후보
    private fun createFixture(withRequest: Boolean): Fixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = workplaceRepository.save(Workplace("알림 연결 테스트 매장 $suffix", "NTF-$suffix"))
            val startAt = now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)
            val schedule = Schedule(workplace, startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                .also { it.publish(now()) }
                .let { scheduleRepository.save(it) }

            val manager = saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
            val otherManager = saveMember(workplace, "manager2-$suffix", WorkplaceRole.MANAGER)
            val requester = saveMember(workplace, "requester-$suffix", WorkplaceRole.EMPLOYEE)
            val target = saveMember(workplace, "target-$suffix", WorkplaceRole.EMPLOYEE)
            val b = saveMember(workplace, "candidateB-$suffix", WorkplaceRole.EMPLOYEE)
            val c = saveMember(workplace, "candidateC-$suffix", WorkplaceRole.EMPLOYEE)
            val shift = shiftRepository.save(Shift(schedule, requester, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))

            val request = if (withRequest) substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN)) else null
            fun candidate(member: WorkplaceMember): CandidateRef {
                val saved = request?.let { substituteCandidateRepository.save(SubstituteCandidate(it, member)) }
                return CandidateRef(saved?.id ?: -1L, requireNotNull(member.id), requireNotNull(member.user.id))
            }

            Fixture(
                workplaceId = requireNotNull(workplace.id),
                scheduleId = requireNotNull(schedule.id),
                shiftId = requireNotNull(shift.id),
                startAt = startAt,
                label = ShiftChangeNotificationPlanner.shiftLabel(startAt, startAt.plusHours(4)),
                manager = ref(manager),
                otherManager = ref(otherManager),
                requester = ref(requester),
                target = ref(target),
                requestId = request?.id ?: -1L,
                candidateB = candidate(b),
                candidateC = candidate(c),
            )
        }
    )

    private fun ref(member: WorkplaceMember) = MemberRef(requireNotNull(member.id), requireNotNull(member.user.id))

    private fun saveMember(workplace: Workplace, name: String, role: WorkplaceRole): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, now().minusDays(30)))
    }

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    companion object {
        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")

        private fun uniqueSuffix(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}
