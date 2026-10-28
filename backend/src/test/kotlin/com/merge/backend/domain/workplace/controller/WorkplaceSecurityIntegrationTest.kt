package com.merge.backend.domain.workplace.controller

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.service.WorkplaceService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkplaceSecurityIntegrationTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var workplaceService: WorkplaceService

    private var workplaceId: Long = 0L
    private lateinit var inviteCode: String
    private lateinit var managerToken: String
    private lateinit var employeeToken: String

    @BeforeEach
    fun setUp() {
        val manager = userRepository.save(
            User("security-manager@test.com", "password", "관리자")
        )

        val employee = userRepository.save(
            User("security-employee@test.com", "password", "직원")
        )

        val created = workplaceService.createWorkplace("보안 테스트 매장", manager)

        workplaceId = requireNotNull(created.workplace.id)
        inviteCode = created.workplace.inviteCode

        workplaceService.joinWorkplace(inviteCode, employee)

        managerToken = userService.genAccessToken(manager)
        employeeToken = userService.genAccessToken(employee)
    }

    @Test
    @DisplayName("유효한 JWT를 가진 관리자는 초대코드를 조회할 수 있다")
    fun managerCanGetInviteCode() {
        mvc.perform(
            get("/api/v1/workplaces/{id}/invite-code", workplaceId)
                .contextPath("/api/v1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
    }

    @Test
    @DisplayName("유효한 JWT가 있어도 직원은 관리자 API에 접근할 수 없다")
    fun employeeCannotGetInviteCode() {
        mvc.perform(
            get("/api/v1/workplaces/{id}/invite-code", workplaceId)
                .contextPath("/api/v1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("WPL-004"))
    }

    @Test
    @DisplayName("JWT 없이 관리자 API에 접근할 수 없다")
    fun unauthenticatedUserCannotGetInviteCode() {
        mvc.perform(
            get("/api/v1/workplaces/{id}/invite-code", workplaceId)
                .contextPath("/api/v1")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("다른 근무지 관리자는 유효한 JWT가 있어도 초대코드를 조회할 수 없다")
    fun otherWorkplaceManagerCannotGetInviteCode() {
        val otherManager = userRepository.save(
            User(
                "security-other@test.com",
                "password",
                "다른 매장 관리자"
            )
        )

        workplaceService.createWorkplace(
            "다른 보안 테스트 매장",
            otherManager
        )

        val otherManagerToken = userService.genAccessToken(otherManager)

        mvc.perform(
            get("/api/v1/workplaces/{id}/invite-code", workplaceId)
                .contextPath("/api/v1")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + otherManagerToken
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("WPL-003"))
    }

}