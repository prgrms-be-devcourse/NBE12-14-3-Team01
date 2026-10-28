package com.merge.backend.domain.workplace.controller

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/** 실제 인증 Filter·Controller·Service·MySQL을 연결한다. Authorization 헤더는 출력하지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MembershipLifecycleApiIntegrationTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var entityManager: EntityManager

    private lateinit var actor: User
    private lateinit var workplace: Workplace
    private lateinit var token: String
    private val joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0)

    @BeforeEach
    fun setUp() {
        actor = persist(User("lifecycle-api-actor@test.com", "pw", "본인"))
        workplace = persist(Workplace("소속 이력 매장", "LIFE0001"))
        token = userService.genAccessToken(actor)
    }

    @Test
    @DisplayName("본인의 종료 이력만 퇴사일 내림차순으로 반환하고 재가입 기간을 합치지 않는다")
    fun historyReturnsOnlyOwnEndedMemberships() {
        val first = persist(WorkplaceMember(workplace, actor, WorkplaceRole.EMPLOYEE,
            joinedAt, joinedAt.plusDays(2)))
        val second = persist(WorkplaceMember(workplace, actor, WorkplaceRole.EMPLOYEE,
            joinedAt.plusDays(3), joinedAt.plusDays(5)))
        persist(WorkplaceMember(workplace, actor, WorkplaceRole.EMPLOYEE, joinedAt.plusDays(6)))
        val otherUser = persist(User("lifecycle-api-other@test.com", "pw", "다른 사용자"))
        persist(WorkplaceMember(workplace, otherUser, WorkplaceRole.EMPLOYEE,
            joinedAt, joinedAt.plusDays(8)))
        entityManager.flush()
        entityManager.clear()

        mvc.perform(get("/api/v1/me/memberships").contextPath("/api/v1")
            .param("status", "ENDED")
            .param("userId", requireNotNull(otherUser.id).toString())
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.memberships.length()").value(2))
            .andExpect(jsonPath("$.data.memberships[0].memberId").value(second.id))
            .andExpect(jsonPath("$.data.memberships[1].memberId").value(first.id))
            .andExpect(jsonPath("$.data.memberships[0].workplaceName").value("소속 이력 매장"))
            .andExpect(jsonPath("$.data.memberships[0].role").value("EMPLOYEE"))
            .andExpect(jsonPath("$.data.memberships[0].joinedAt").exists())
            .andExpect(jsonPath("$.data.memberships[0].leftAt").exists())
    }

    @Test
    @DisplayName("현재 소속과 관리자 권한이 없어도 자신의 종료 이력을 조회한다")
    fun historyDoesNotRequireCurrentMembership() {
        val ended = persist(WorkplaceMember(workplace, actor, WorkplaceRole.EMPLOYEE,
            joinedAt, joinedAt.plusDays(2)))
        entityManager.flush()
        entityManager.clear()

        mvc.perform(get("/api/v1/me/memberships").contextPath("/api/v1")
            .param("status", "ENDED").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memberships.length()").value(1))
            .andExpect(jsonPath("$.data.memberships[0].memberId").value(ended.id))
    }

    @Test
    @DisplayName("종료 이력이 없으면 정상 빈 목록을 반환한다")
    fun historyReturnsEmptyList() {
        mvc.perform(get("/api/v1/me/memberships").contextPath("/api/v1")
            .param("status", "ENDED").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memberships").isEmpty)
    }

    @Test
    @DisplayName("인증되지 않은 소속 이력 요청은 거부한다")
    fun historyRequiresAuthentication() {
        mvc.perform(get("/api/v1/me/memberships").contextPath("/api/v1").param("status", "ENDED"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("ENDED 이외의 status와 status 누락은 기존 COM-003으로 거부한다")
    fun historyRejectsUnsupportedOrMissingStatus() {
        for (value in listOf("CURRENT", "ended", "")) {
            mvc.perform(get("/api/v1/me/memberships").contextPath("/api/v1")
                .param("status", value).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("COM-003"))
        }
        mvc.perform(get("/api/v1/me/memberships").contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("COM-003"))
    }

    @Test
    @DisplayName("현재 관리자는 Body 없이 초대 코드를 재발급하고 기존 GET으로 새 코드를 조회한다")
    fun managerRegeneratesInviteCodeWithoutBody() {
        persist(WorkplaceMember(workplace, actor, WorkplaceRole.MANAGER, joinedAt))
        val workplaceId = requireNotNull(workplace.id)
        val previousCode = workplace.inviteCode
        entityManager.flush()

        mvc.perform(post("/api/v1/workplaces/{id}/invite-code", workplaceId).contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.workplaceId").value(workplaceId))
            .andExpect(jsonPath("$.data.inviteCode").isString)
        entityManager.flush()
        entityManager.clear()
        val currentCode = entityManager.find(Workplace::class.java, workplaceId).inviteCode
        assertThat(currentCode).isNotEqualTo(previousCode)

        mvc.perform(get("/api/v1/workplaces/{id}/invite-code", workplaceId).contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.inviteCode").value(currentCode))
    }

    @Test
    @DisplayName("직원의 초대 코드 재발급은 WPL-004로 거부하고 코드를 보존한다")
    fun employeeCannotRegenerateInviteCode() {
        persist(WorkplaceMember(workplace, actor, WorkplaceRole.EMPLOYEE, joinedAt))
        val previousCode = workplace.inviteCode

        mvc.perform(post("/api/v1/workplaces/{id}/invite-code", workplace.id).contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("WPL-004"))
        entityManager.flush()
        entityManager.clear()
        assertThat(entityManager.find(Workplace::class.java, workplace.id).inviteCode).isEqualTo(previousCode)
    }

    @Test
    @DisplayName("다른 근무지 관리자의 재발급은 WPL-005로 거부하고 코드를 보존한다")
    fun otherWorkplaceManagerCannotRegenerateInviteCode() {
        val other = persist(Workplace("다른 매장", "LIFE0002"))
        persist(WorkplaceMember(other, actor, WorkplaceRole.MANAGER, joinedAt))
        val previousCode = workplace.inviteCode

        mvc.perform(post("/api/v1/workplaces/{id}/invite-code", workplace.id).contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("WPL-005"))
        entityManager.flush()
        entityManager.clear()
        assertThat(entityManager.find(Workplace::class.java, workplace.id).inviteCode).isEqualTo(previousCode)
    }

    @Test
    @DisplayName("인증되지 않은 초대 코드 재발급 요청은 거부한다")
    fun regenerationRequiresAuthentication() {
        mvc.perform(post("/api/v1/workplaces/{id}/invite-code", workplace.id).contextPath("/api/v1"))
            .andExpect(status().isUnauthorized)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }
}
