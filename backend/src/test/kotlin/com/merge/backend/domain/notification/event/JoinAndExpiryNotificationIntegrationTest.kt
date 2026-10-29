package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.substitute.service.SubstituteCandidateService
import com.merge.backend.domain.substitute.service.SubstituteRequestService
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.domain.workplace.service.WorkplaceService
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import com.merge.backend.global.exception.BusinessException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// 알림 계약 중 Test가 없던 항목 (V2-N-19 다른 근무지 관리자 0건, V2-N-21 가입 Rollback → 알림 0건, NT-T8 SUBSTITUTE_EXPIRED 미발행)
// 실제 Command를 부르고 Commit 뒤 알림 Row를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class JoinAndExpiryNotificationIntegrationTest {

    @Autowired
    private lateinit var workplaceService: WorkplaceService

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

    @Test
    @DisplayName("V2-N-19: 가입 알림은 그 근무지의 현재 관리자에게만 가고, 같은 사람이 관리자인 다른 근무지 소속에는 가지 않는다")
    fun joinNotifiesOnlyManagersOfThatWorkplace() {
        // given: 관리자 M은 다른 근무지에서도 관리자
        val place = createWorkplace()
        val other = createWorkplace(manager = place.manager.user)
        val newcomer = saveUser("newcomer")
        val since = maxNotificationId()

        // when
        workplaceService.joinWorkplace(place.inviteCode, newcomer)

        // then
        assertThat(typesOf(place.manager.memberId, since)).containsExactly(NotificationType.WORKPLACE_MEMBER_JOINED.name)
        assertThat(typesOf(other.manager.memberId, since)).isEmpty()
    }

    @Test
    @DisplayName("V2-N-21: 이미 현재 소속인 사람의 가입은 WPL-002로 거부되고 가입 알림은 0건이다")
    fun rejectedJoinCreatesNoNotification() {
        // given
        val place = createWorkplace()
        val employee = saveUser("employee")
        workplaceService.joinWorkplace(place.inviteCode, employee)
        val since = maxNotificationId()

        // when
        val error = assertThrows<BusinessException> { workplaceService.joinWorkplace(place.inviteCode, employee) }

        // then
        assertThat(error.errorCode).isEqualTo(WorkplaceErrorCode.ALREADY_JOINED_WORKPLACE)
        assertThat(typesOf(place.manager.memberId, since)).isEmpty()
    }

    @Test
    @DisplayName("V2-N-21: 가입 Event가 발행된 뒤 Transaction이 Rollback되면 소속 Row와 가입 알림 모두 0건이다 (Commit 후에만 저장)")
    fun rolledBackJoinCreatesNoNotification() {
        // given
        val place = createWorkplace()
        val employee = saveUser("employee")
        val since = maxNotificationId()

        // when: 가입은 끝까지 실행됐지만(Event 발행) 바깥 Transaction이 Rollback
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            workplaceService.joinWorkplace(place.inviteCode, employee)
            status.setRollbackOnly()
        }

        // then
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workplace_member WHERE workplace_id = ? AND user_id = ?",
                Long::class.java, place.workplaceId, employee.id,
            ),
        ).isZero()
        assertThat(typesOf(place.manager.memberId, since)).isEmpty()
    }

    @Test
    @DisplayName("NT-T8: 승인 전에 근무가 시작돼 대타 요청이 계산된 만료가 되어도 SUBSTITUTE_EXPIRED 알림은 만들지 않는다")
    fun expiredRequestCreatesNoExpiredNotification() {
        // given: A의 OPEN 요청, B 수락 (근무는 하루 뒤)
        val place = createWorkplace()
        val requester = saveMember(place.workplace, "requester", WorkplaceRole.EMPLOYEE)
        val accepter = saveMember(place.workplace, "accepter", WorkplaceRole.EMPLOYEE)
        val startAt = LocalDateTime.now(testClock).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0)
        val requestId = createAcceptedRequest(place.workplace, requester, accepter, startAt)
        val since = maxNotificationId()

        // when: 근무 시작 시각이 지난 뒤 만료를 계산하는 조회들을 실행
        testClock.setInstant(FIXED_NOW.plusSeconds(3 * 86_400))
        val sent = substituteRequestService.getSentRequests(requireNotNull(requester.user.id))
        val closedForAccepter = substituteCandidateService.findAcceptedClosedRequests(requireNotNull(accepter.user.id))

        // then: 화면에는 만료로 보이지만 DB는 그대로이고 만료 알림은 0건 (G03-D-20 결정 전까지 발행하지 않음)
        assertThat(sent.single { it.requestId == requestId }.closeReason).isEqualTo(RequestCloseReason.EXPIRED)
        assertThat(closedForAccepter.single { it.requestId == requestId }.closeReason).isEqualTo(RequestCloseReason.EXPIRED)
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM substitute_request WHERE id = ?", String::class.java, requestId))
            .isEqualTo(RequestStatus.ACCEPTED.name)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE type = ? AND id > ?",
                Long::class.java, NotificationType.SUBSTITUTE_EXPIRED.name, since,
            ),
        ).isZero()
    }

    // ===================== 헬퍼 =====================

    private fun maxNotificationId(): Long =
        jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM notification", Long::class.java) ?: 0L

    private fun typesOf(memberId: Long, since: Long): List<String?> =
        jdbcTemplate.queryForList(
            "SELECT type FROM notification WHERE recipient_member_id = ? AND id > ? ORDER BY id",
            String::class.java, memberId, since,
        )

    private data class ManagerRef(val memberId: Long, val user: User)

    private data class Place(val workplace: Workplace, val workplaceId: Long, val inviteCode: String, val manager: ManagerRef)

    private fun createWorkplace(manager: User? = null): Place = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val suffix = UUID.randomUUID().toString().substring(0, 8)
            val workplace = workplaceRepository.save(Workplace("알림 계약 테스트 매장 $suffix", "NC-$suffix"))
            val managerUser = manager ?: saveUser("manager")
            val managerMember = workplaceMemberRepository.save(
                WorkplaceMember(workplace, managerUser, WorkplaceRole.MANAGER, LocalDateTime.now(testClock).minusDays(30)),
            )
            Place(workplace, requireNotNull(workplace.id), workplace.inviteCode, ManagerRef(requireNotNull(managerMember.id), managerUser))
        },
    )

    private fun createAcceptedRequest(
        workplace: Workplace,
        requester: WorkplaceMember,
        accepter: WorkplaceMember,
        startAt: LocalDateTime,
    ): Long {
        val requestId = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val schedule = Schedule(workplace, startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                    .also { it.publish(LocalDateTime.now(testClock)) }
                    .let { scheduleRepository.save(it) }
                val shift = shiftRepository.save(Shift(schedule, requester, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))
                val request = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))
                val candidate = substituteCandidateRepository.save(SubstituteCandidate(request, accepter))
                Pair(requireNotNull(request.id), requireNotNull(candidate.id))
            },
        )
        substituteCandidateService.acceptCandidate(requestId.second, requireNotNull(accepter.user.id))
        return requestId.first
    }

    private fun saveMember(workplace: Workplace, name: String, role: WorkplaceRole): WorkplaceMember =
        workplaceMemberRepository.save(WorkplaceMember(workplace, saveUser(name), role, LocalDateTime.now(testClock).minusDays(30)))

    private fun saveUser(name: String): User {
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        return userRepository.save(User("$name-$suffix@test.com", "password-hash", name))
    }

    companion object {
        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")
    }
}
