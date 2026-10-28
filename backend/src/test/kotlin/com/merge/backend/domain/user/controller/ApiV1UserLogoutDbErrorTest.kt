package com.merge.backend.domain.user.controller

import com.merge.backend.domain.user.repository.UserRepository
import jakarta.servlet.http.Cookie
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiV1UserLogoutDbErrorTest {

    @Autowired
    private lateinit var mvc: MockMvc

    // 진짜 Repository를 감싸서 조건부 UPDATE 때만 DB 오류를 일으킴
    @MockitoSpyBean
    private lateinit var userRepository: UserRepository

    // 같은 DB를 다른 테스트와 같이 쓰므로 테스트마다 다른 이메일
    private lateinit var email: String

    @BeforeEach
    fun setUp() {
        email = "logout-db-error-${UUID.randomUUID()}@test.com"

        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$email", "password": "$PASSWORD", "name": "오류유저"}
                    """.trimIndent()))
            .andExpect(status().isCreated())
    }

    // 실제 DB에 남으니까 테스트마다 지움
    @AfterEach
    fun tearDown() {
        userRepository.findByEmail(email).ifPresent(userRepository::delete)
    }

    @Test
    @DisplayName("로그아웃 중 DB 오류가 나면 500이고 성공으로 응답하지 않으며 DB와 쿠키를 그대로 둔다")
    fun logoutWithDbErrorFailsWithoutChange() {
        // given
        val loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$email", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andReturn()
        val refreshToken = requireNotNull(loginResult.response.getCookie("refreshToken")).value

        // 조건부 UPDATE를 실제로 실행한 다음 DB 오류를 던짐 → Transaction 전체가 되돌려져야 함
        // Repository는 인터페이스라 callRealMethod()가 안 되므로, Spy의 기본 동작(진짜 Repository 호출)을 씀
        val realRepository = mockingDetails(userRepository).mockCreationSettings.defaultAnswer
        var updatedRows: Any? = null
        doAnswer { invocation ->
            updatedRows = realRepository.answer(invocation)
            throw DataAccessResourceFailureException("주입한 DB 오류")
        }.whenever(userRepository).clearRefreshTokenIfMatches(any(), any())

        // when
        val result = mvc.perform(post("/api/v1/auth/logout").contextPath("/api/v1")
                .header("Origin", ALLOWED_ORIGIN)
                .cookie(Cookie("refreshToken", refreshToken)))

        // then: UPDATE는 실제로 1행을 바꿨지만 되돌려졌고, 성공으로 보이지 않고, 쿠키 삭제 헤더도 없음
        assertThat(updatedRows).isEqualTo(1)
        result.andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-500"))
            .andExpect(header().doesNotExist("Set-Cookie"))
        assertThat(userRepository.findByEmail(email).orElseThrow().refreshToken).isEqualTo(refreshToken)
    }

    companion object {
        private const val PASSWORD = "password1"

        // application.yml의 custom.cors.allowed-origins 기본값
        private const val ALLOWED_ORIGIN = "http://localhost:3000"
    }
}
