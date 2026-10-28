package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.entity.Notification
import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.notification.repository.NotificationRepository
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.service.ShiftService
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
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// 새 알림 저장 실패가 업무 결과를 바꾸지 않고(NT-T1, R2), 한 사람의 실패가 다른 사람의 알림을 막지 않는다(R5)
// SubstituteNotificationAfterCommitIntegrationTest와 같은 Spy 설정이라 Spring Context를 함께 쓴다
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class ShiftChangeNotificationFailureIntegrationTest {
    @MockitoSpyBean private lateinit var notificationRepository: NotificationRepository
    @Autowired private lateinit var shiftService: ShiftService
    @Autowired private lateinit var substituteCandidateService: SubstituteCandidateService
    @Autowired private lateinit var substituteRequestRepository: SubstituteRequestRepository
    @Autowired private lateinit var substituteCandidateRepository: SubstituteCandidateRepository
    @Autowired private lateinit var shiftRepository: ShiftRepository
    @Autowired private lateinit var scheduleRepository: ScheduleRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var workplaceRepository: WorkplaceRepository
    @Autowired private lateinit var workplaceMemberRepository: WorkplaceMemberRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var testClock: TestClock

    @BeforeEach
    fun fixClock() {
        testClock.setInstant(FIXED_NOW)
    }

    @Test
    @DisplayName("NT-T1: 새 알림 저장이 모두 실패해도 근무 취소·대타 종료는 Commit되고 응답 수도 그대로다")
    fun allNotificationFailuresDoNotChangeBusinessResult() {
        // given
        val f = createFixture()
        val since = maxNotificationId()
        doAnswer { throw IllegalStateException("injected notification failure") }
            .`when`(notificationRepository).save(any<Notification>())

        // when
        val result = shiftService.cancelPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, f.managerUserId)

        // then
        assertThat(result.closedSubstituteRequests).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM shift WHERE id = ?", String::class.java, f.shiftId)).isEqualTo("CANCELLED")
        assertThat(jdbcTemplate.queryForObject("SELECT close_reason FROM substitute_request WHERE id = ?", String::class.java, f.requestId))
            .isEqualTo("SHIFT_CHANGED")
        assertThat(notificationCountSince(since)).isZero()
    }

    @Test
    @DisplayName("R5: 원래 담당 한 명의 알림 저장이 실패해도 수락자·다른 관리자의 알림은 저장된다")
    fun oneRecipientFailureDoesNotDropOthers() {
        // given
        val f = createFixture()
        val since = maxNotificationId()
        // 원래 담당에게 가는 저장만 실패시키고 나머지는 실제 저장 (Spy 기본 동작)
        doAnswer { throw IllegalStateException("injected recipient failure") }
            .`when`(notificationRepository).save(argThat<Notification> { recipientMember.id == f.requesterMemberId })

        // when
        shiftService.cancelPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, f.managerUserId)

        // then
        assertThat(typesOf(f.requesterMemberId, since)).isEmpty()
        assertThat(typesOf(f.accepterMemberId, since)).containsExactly(NotificationType.SUBSTITUTE_SHIFT_CHANGED.name)
        assertThat(typesOf(f.otherManagerMemberId, since)).containsExactly(NotificationType.SUBSTITUTE_SHIFT_CHANGED.name)
    }

    private fun maxNotificationId(): Long =
        jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM notification", Long::class.java) ?: 0L

    private fun notificationCountSince(since: Long): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE id > ?", Long::class.java, since) ?: 0L

    private fun typesOf(memberId: Long, since: Long): List<String?> =
        jdbcTemplate.queryForList(
            "SELECT type FROM notification WHERE recipient_member_id = ? AND id > ? ORDER BY id",
            String::class.java,
            memberId,
            since,
        )

    private data class Fixture(
        val workplaceId: Long,
        val scheduleId: Long,
        val shiftId: Long,
        val managerUserId: Long,
        val otherManagerMemberId: Long,
        val requesterMemberId: Long,
        val accepterMemberId: Long,
        val requestId: Long,
    )

    // 관리자 M(실행)·M2, A의 공개 근무에 A가 요청하고 B가 수락한 상태 (Request ACCEPTED)
    private fun createFixture(): Fixture {
        val f = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val suffix = UUID.randomUUID().toString().substring(0, 8)
                val workplace = workplaceRepository.save(Workplace("알림 실패 테스트 매장 $suffix", "NTX-$suffix"))
                val startAt = now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)
                val schedule = Schedule(workplace, startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                    .also { it.publish(now()) }
                    .let { scheduleRepository.save(it) }
                val manager = saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
                val otherManager = saveMember(workplace, "manager2-$suffix", WorkplaceRole.MANAGER)
                val requester = saveMember(workplace, "requester-$suffix", WorkplaceRole.EMPLOYEE)
                val accepter = saveMember(workplace, "accepter-$suffix", WorkplaceRole.EMPLOYEE)
                val shift = shiftRepository.save(Shift(schedule, requester, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))
                val request = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))
                val candidate = substituteCandidateRepository.save(SubstituteCandidate(request, accepter))

                listOf(
                    Fixture(
                        workplaceId = requireNotNull(workplace.id),
                        scheduleId = requireNotNull(schedule.id),
                        shiftId = requireNotNull(shift.id),
                        managerUserId = requireNotNull(manager.user.id),
                        otherManagerMemberId = requireNotNull(otherManager.id),
                        requesterMemberId = requireNotNull(requester.id),
                        accepterMemberId = requireNotNull(accepter.id),
                        requestId = requireNotNull(request.id),
                    ) to (requireNotNull(candidate.id) to requireNotNull(accepter.user.id)),
                ).single()
            },
        )
        substituteCandidateService.acceptCandidate(f.second.first, f.second.second)

        return f.first
    }

    private fun saveMember(workplace: Workplace, name: String, role: WorkplaceRole): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, now().minusDays(30)))
    }

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    companion object {
        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")
    }
}
