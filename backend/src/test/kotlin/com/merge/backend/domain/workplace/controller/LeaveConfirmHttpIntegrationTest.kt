package com.merge.backend.domain.workplace.controller

import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.substitute.service.SubstituteCandidateService
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

// 퇴사 확정 POST (API-LV-02) 활성화 확인: 실제 Security Filter + JWT + 실제 V2·V3·V4 Transaction + Commit 후 알림 (V5-N-20)
// 미리보기도 HTTP로 받아 그대로 확정한다. JWT 만료 검사가 실제 시간이라 TestClock 대신 실제 Clock을 쓴다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveConfirmHttpIntegrationTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var substituteCandidateService: SubstituteCandidateService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var substituteRequestRepository: SubstituteRequestRepository

    @Autowired
    private lateinit var substituteCandidateRepository: SubstituteCandidateRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @DisplayName("인증 없이 퇴사 확정을 보내면 401이고, 직원이 보내면 403 WPL-004이며 소속은 그대로다")
    fun postRequiresAuthenticationAndManager() {
        val f = createFixture()
        val body = """{"previewContext":"x","decisions":[]}"""

        mvc.perform(post(LEAVE, f.workplaceId, f.leaver.memberId).contextPath(CONTEXT).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        mvc.perform(
            post(LEAVE, f.workplaceId, f.leaver.memberId).contextPath(CONTEXT).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(f.target.user)}"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("WPL-004"))

        assertThat(leftAtOf(f.leaver.memberId)).isNull()
    }

    @Test
    @DisplayName("미리보기 → 확정을 HTTP로 실행하면 인계·취소·DRAFT 삭제·대타 정리·소속 종료가 Commit되고, 알림은 인계받은 직원·관련자에게만 간다 (퇴사자·실행 관리자 0건)")
    fun previewThenConfirmCommitsEverythingAndNotifies() {
        // given
        val f = createFixture()
        val managerToken = token(f.manager.user)
        val preview = objectMapper.readTree(
            mvc.perform(get(PREVIEW, f.workplaceId, f.leaver.memberId).contextPath(CONTEXT).header(HttpHeaders.AUTHORIZATION, "Bearer $managerToken"))
                .andExpect(status().isOk)
                .andReturn().response.contentAsString,
        ).get("data")
        // Jackson 3 JsonNode에는 자체 map(Function)이 있어 Iterable로 바꿔 원소마다 처리
        val decisions = preview.get("publishedShifts").toList().map { shift ->
            val shiftId = shift.get("shiftId").asLong()
            buildMap {
                put("shiftId", shiftId)
                put("shiftStartAt", shift.get("startAt").asString())
                put("shiftEndAt", shift.get("endAt").asString())
                if (shiftId == f.reassignShiftId) {
                    put("action", "REASSIGN")
                    put("targetMemberId", f.target.memberId)
                } else {
                    put("action", "CANCEL")
                }
            }
        }
        val body = objectMapper.writeValueAsString(
            mapOf("previewContext" to preview.get("previewContext").asString(), "decisions" to decisions),
        )
        val since = maxNotificationId()

        // when
        mvc.perform(
            post(LEAVE, f.workplaceId, f.leaver.memberId).contextPath(CONTEXT).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $managerToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.reassignedShifts.length()").value(1))
            .andExpect(jsonPath("$.data.cancelledShifts.length()").value(1))
            .andExpect(jsonPath("$.data.deletedDraftShifts.length()").value(1))
            .andExpect(jsonPath("$.data.closedRequests.total").value(3))
            .andExpect(jsonPath("$.data.closedRequests.byReason.SHIFT_CHANGED").value(1))
            .andExpect(jsonPath("$.data.closedRequests.byReason.CANDIDATE_INELIGIBLE").value(2))

        // then: DB
        assertThat(leftAtOf(f.leaver.memberId)).isNotNull()
        assertThat(jdbcTemplate.queryForObject("SELECT member_id FROM shift WHERE id = ?", Long::class.java, f.reassignShiftId))
            .isEqualTo(f.target.memberId)
        assertThat(jdbcTemplate.queryForMap("SELECT status, cancellation_reason FROM shift WHERE id = ?", f.cancelShiftId))
            .containsEntry("status", "CANCELLED").containsEntry("cancellation_reason", "MEMBER_LEFT")
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shift WHERE id = ?", Long::class.java, f.draftShiftId)).isZero()
        assertThat(jdbcTemplate.queryForObject("SELECT reason FROM shift_assignment_history WHERE shift_id = ?", String::class.java, f.reassignShiftId))
            .isEqualTo("MEMBER_LEFT")
        assertThat(closeReasonOf(f.leaverRequestId)).isEqualTo("SHIFT_CHANGED")
        assertThat(closeReasonOf(f.employeeRequestId)).isEqualTo("CANDIDATE_INELIGIBLE")
        assertThat(closeReasonOf(f.managerRequestId)).isEqualTo("CANDIDATE_INELIGIBLE")

        // then: 알림 (NOTIFICATION-TYPES §3-1·§3-3·§6-3, V5-N-20)
        assertThat(typesOf(f.target, since)).containsExactly(NotificationType.SHIFT_ASSIGNED.name)
        assertThat(typesOf(f.accepter, since)).containsExactly(NotificationType.SUBSTITUTE_SHIFT_CHANGED.name)
        assertThat(typesOf(f.requester, since)).containsExactly(NotificationType.SUBSTITUTE_CANDIDATE_INELIGIBLE.name)
        // 다른 현재 관리자: 근무 변경 종료 1건 + 자격 상실 2건
        assertThat(typesOf(f.otherManager, since)).containsExactlyInAnyOrder(
            NotificationType.SUBSTITUTE_SHIFT_CHANGED.name,
            NotificationType.SUBSTITUTE_CANDIDATE_INELIGIBLE.name,
            NotificationType.SUBSTITUTE_CANDIDATE_INELIGIBLE.name,
        )
        // 실행 관리자 M: 관리자 수신은 0건, 자기가 요청자인 요청의 자격 상실만 1건 (요청자 문구)
        assertThat(messagesOf(f.manager, since)).singleElement().satisfies({
            assertThat(it).contains("필요하면 다시 요청해 주세요")
        })
        assertThat(typesOf(f.leaver, since)).isEmpty()
    }

    // ===================== 헬퍼 =====================

    private fun token(user: User): String = userService.genAccessToken(user)

    private fun leftAtOf(memberId: Long): LocalDateTime? =
        jdbcTemplate.queryForObject("SELECT left_at FROM workplace_member WHERE id = ?", LocalDateTime::class.java, memberId)

    private fun closeReasonOf(requestId: Long): String? =
        jdbcTemplate.queryForObject("SELECT close_reason FROM substitute_request WHERE id = ?", String::class.java, requestId)

    private fun maxNotificationId(): Long =
        jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM notification", Long::class.java) ?: 0L

    private fun typesOf(member: MemberRef, since: Long): List<String?> =
        jdbcTemplate.queryForList(
            "SELECT type FROM notification WHERE recipient_member_id = ? AND id > ? ORDER BY id",
            String::class.java, member.memberId, since,
        )

    private fun messagesOf(member: MemberRef, since: Long): List<String?> =
        jdbcTemplate.queryForList(
            "SELECT message FROM notification WHERE recipient_member_id = ? AND id > ? ORDER BY id",
            String::class.java, member.memberId, since,
        )

    private data class MemberRef(val memberId: Long, val user: User)

    private data class Fixture(
        val workplaceId: Long,
        val manager: MemberRef,
        val otherManager: MemberRef,
        val leaver: MemberRef,
        val target: MemberRef,
        val accepter: MemberRef,
        val requester: MemberRef,
        val reassignShiftId: Long,
        val cancelShiftId: Long,
        val draftShiftId: Long,
        val leaverRequestId: Long,
        val employeeRequestId: Long,
        val managerRequestId: Long,
    )

    // 관리자 M(실행)·M2, 퇴사자 E, 인계 대상 T, 수락자 B, 요청자 R.
    // E의 공개 근무 2개(하나는 인계, 하나는 취소)와 DRAFT 1개, E 근무의 요청(B 수락), E가 유일한 대기 후보인 R의 요청과 M의 요청
    private fun createFixture(): Fixture {
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        val day = { days: Long -> now.plusDays(days).withHour(10).withMinute(0) }
        val ids = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val suffix = UUID.randomUUID().toString().substring(0, 8)
                val workplace = workplaceRepository.save(Workplace("퇴사 확정 HTTP 테스트 $suffix", "LC-$suffix"))
                fun member(name: String, role: WorkplaceRole): WorkplaceMember {
                    val user = userRepository.save(User("$name-$suffix@test.com", "password-hash", name))
                    return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, now.minusDays(30)))
                }
                val m = member("manager", WorkplaceRole.MANAGER)
                val m2 = member("manager2", WorkplaceRole.MANAGER)
                val e = member("leaver", WorkplaceRole.EMPLOYEE)
                val t = member("target", WorkplaceRole.EMPLOYEE)
                val b = member("accepter", WorkplaceRole.EMPLOYEE)
                val r = member("requester", WorkplaceRole.EMPLOYEE)
                val published = scheduleRepository.save(
                    Schedule(workplace, day(3).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).also { it.publish(now) },
                )
                val draft = scheduleRepository.save(
                    Schedule(workplace, day(3).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(2)),
                )
                fun shift(schedule: Schedule, owner: WorkplaceMember, start: LocalDateTime) =
                    shiftRepository.save(Shift(schedule, owner, start, start.plusHours(4), ShiftStatus.SCHEDULED))
                val reassign = shift(published, e, day(3))
                val cancel = shift(published, e, day(4))
                val draftShift = shift(draft, e, day(17))
                val leaverRequest = substituteRequestRepository.save(SubstituteRequest(reassign, e, RequestStatus.OPEN))
                val accepterCandidate = substituteCandidateRepository.save(SubstituteCandidate(leaverRequest, b))
                val employeeRequest = substituteRequestRepository.save(SubstituteRequest(shift(published, r, day(5)), r, RequestStatus.OPEN))
                substituteCandidateRepository.save(SubstituteCandidate(employeeRequest, e))
                val managerRequest = substituteRequestRepository.save(SubstituteRequest(shift(published, m, day(6)), m, RequestStatus.OPEN))
                substituteCandidateRepository.save(SubstituteCandidate(managerRequest, e))

                fun ref(member: WorkplaceMember) = MemberRef(requireNotNull(member.id), member.user)
                Pair(
                    Fixture(
                        workplaceId = requireNotNull(workplace.id),
                        manager = ref(m), otherManager = ref(m2), leaver = ref(e), target = ref(t), accepter = ref(b), requester = ref(r),
                        reassignShiftId = requireNotNull(reassign.id),
                        cancelShiftId = requireNotNull(cancel.id),
                        draftShiftId = requireNotNull(draftShift.id),
                        leaverRequestId = requireNotNull(leaverRequest.id),
                        employeeRequestId = requireNotNull(employeeRequest.id),
                        managerRequestId = requireNotNull(managerRequest.id),
                    ),
                    requireNotNull(accepterCandidate.id),
                )
            },
        )
        // B가 E의 요청을 수락 (실제 Command)
        substituteCandidateService.acceptCandidate(ids.second, requireNotNull(ids.first.accepter.user.id))
        return ids.first
    }

    private companion object {
        // application.yml의 server.servlet.context-path
        const val CONTEXT = "/api/v1"
        const val PREVIEW = "/api/v1/workplaces/{workplaceId}/members/{memberId}/leave-preview"
        const val LEAVE = "/api/v1/workplaces/{workplaceId}/members/{memberId}/leave"
    }
}
