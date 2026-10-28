package com.merge.backend.domain.user.controller

import com.jayway.jsonpath.JsonPath
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.standard.Ut
import jakarta.servlet.http.Cookie
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiV1UserLogoutTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Value("\${custom.jwt.secret-key}")
    private lateinit var secretKey: String

    // 같은 DB를 다른 테스트와 같이 쓰므로 테스트마다 다른 이메일
    private lateinit var email: String

    @BeforeEach
    fun setUp() {
        email = "logout-${UUID.randomUUID()}@test.com"

        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$email", "password": "$PASSWORD", "name": "로그아웃유저"}
                    """.trimIndent()))
            .andExpect(status().isCreated())
    }

    // 실제 DB에 남으니까 테스트마다 지움
    @AfterEach
    fun tearDown() {
        userRepository.findByEmail(email).ifPresent(userRepository::delete)
    }

    private fun login(): MvcResult =
        mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$email", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andReturn()

    private fun refreshTokenOf(result: MvcResult): String =
        requireNotNull(result.response.getCookie("refreshToken")).value

    // 브라우저처럼 허용된 출처(Origin)를 붙여서 로그아웃
    private fun logout(refreshToken: String?): ResultActions {
        val request = post("/api/v1/auth/logout").contextPath("/api/v1")
            .header("Origin", ALLOWED_ORIGIN)
        if (refreshToken != null) {
            request.cookie(Cookie("refreshToken", refreshToken))
        }
        return mvc.perform(request)
    }

    private fun savedRefreshToken(): String? =
        userRepository.findByEmail(email).orElseThrow().refreshToken

    // 로그아웃은 쿠키 삭제 헤더(Max-Age=0)를 보내야 함
    private fun ResultActions.andExpectLogoutSuccess(): ResultActions =
        andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.message").value("로그아웃되었습니다"))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(cookie().maxAge("refreshToken", 0))
            .andExpect(cookie().value("refreshToken", ""))

    @Test
    @DisplayName("로그인한 쿠키로 로그아웃하면 DB의 Refresh Token을 지우고 쿠키 삭제 헤더를 보낸다")
    fun logoutClearsRefreshTokenAndDeletesCookie() {
        // given
        val refreshToken = refreshTokenOf(login())
        assertThat(savedRefreshToken()).isEqualTo(refreshToken)

        // when
        val result = logout(refreshToken)

        // then
        result.andExpectLogoutSuccess()
        assertThat(savedRefreshToken()).isNull()
    }

    @Test
    @DisplayName("예전 기기의 쿠키로 로그아웃해도 다른 기기의 새 로그인은 그대로 남는다")
    fun logoutWithOldDeviceTokenKeepsNewerLogin() {
        // given: 기기 A 로그인 뒤 기기 B 로그인 (DB에는 B 토큰만 남음)
        val oldDeviceToken = refreshTokenOf(login())
        val newDeviceToken = refreshTokenOf(login())

        // when
        val result = logout(oldDeviceToken)

        // then
        result.andExpectLogoutSuccess()
        assertThat(savedRefreshToken()).isEqualTo(newDeviceToken)
        mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1")
                .cookie(Cookie("refreshToken", newDeviceToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃하면 DB는 그대로 두고 성공으로 응답한다")
    fun logoutWithoutCookieSucceedsWithoutChange() {
        // given
        val refreshToken = refreshTokenOf(login())

        // when
        val result = logout(null)

        // then
        result.andExpectLogoutSuccess()
        assertThat(savedRefreshToken()).isEqualTo(refreshToken)
    }

    @Test
    @DisplayName("다른 비밀키로 서명한 쿠키로 로그아웃하면 DB는 그대로 두고 성공으로 응답한다")
    fun logoutWithForgedTokenSucceedsWithoutChange() {
        // given
        val refreshToken = refreshTokenOf(login())
        val user = userRepository.findByEmail(email).orElseThrow()
        val forgedToken = Ut.jwt.toString(
            "attacker-secret-key-attacker-secret-key-1234",
            REFRESH_EXPIRE_MILLS,
            java.util.Map.of<String, Any>("id", requireNotNull(user.id), "type", "refresh"),
        )

        // when
        val result = logout(forgedToken)

        // then
        result.andExpectLogoutSuccess()
        assertThat(savedRefreshToken()).isEqualTo(refreshToken)
    }

    @Test
    @DisplayName("만료된 쿠키로 로그아웃하면 DB는 그대로 두고 성공으로 응답한다")
    fun logoutWithExpiredTokenSucceedsWithoutChange() {
        // given
        val refreshToken = refreshTokenOf(login())
        val user = userRepository.findByEmail(email).orElseThrow()
        // 서버 비밀키로 서명했지만 만료 시각이 이미 지난 토큰
        val expiredToken = Ut.jwt.toString(
            secretKey,
            -1000L,
            java.util.Map.of<String, Any>("id", requireNotNull(user.id), "type", "refresh"),
        )

        // when
        val result = logout(expiredToken)

        // then
        result.andExpectLogoutSuccess()
        assertThat(savedRefreshToken()).isEqualTo(refreshToken)
    }

    @Test
    @DisplayName("Access Token을 쿠키에 넣어 로그아웃하면 DB는 그대로 두고 성공으로 응답한다")
    fun logoutWithAccessTokenAsCookieSucceedsWithoutChange() {
        // given
        val loginResult = login()
        val refreshToken = refreshTokenOf(loginResult)
        val accessToken: String = JsonPath.read(loginResult.response.contentAsString, "$.data.accessToken")

        // when
        val result = logout(accessToken)

        // then
        result.andExpectLogoutSuccess()
        assertThat(savedRefreshToken()).isEqualTo(refreshToken)
    }

    @Test
    @DisplayName("로그아웃한 Refresh Token으로 재발급하면 401과 AUTH-003을 반환한다")
    fun refreshAfterLogoutFails() {
        // given
        val refreshToken = refreshTokenOf(login())
        logout(refreshToken).andExpectLogoutSuccess()

        // when
        val result = mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1")
                .cookie(Cookie("refreshToken", refreshToken)))

        // then
        result.andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-003"))
    }

    @Test
    @DisplayName("Access Token이 없거나 잘못돼도 실제 보안 필터를 지나 로그아웃된다")
    fun logoutWithoutValidAccessTokenPassesSecurityFilter() {
        // given: 같은 조건으로 보호된 API는 401 → 보안 필터가 실제로 동작 중
        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-004"))
        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer invalid-access-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-006"))

        // when & then: Authorization 헤더 없이 로그아웃 → Controller까지 와서 처리됨
        logout(refreshTokenOf(login())).andExpectLogoutSuccess()
        assertThat(savedRefreshToken()).isNull()

        // when & then: 만료되는 등 잘못된 Access Token이 붙어 와도 처리됨
        val refreshToken = refreshTokenOf(login())
        mvc.perform(post("/api/v1/auth/logout").contextPath("/api/v1")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Authorization", "Bearer invalid-access-token")
                .cookie(Cookie("refreshToken", refreshToken)))
            .andExpectLogoutSuccess()
        assertThat(savedRefreshToken()).isNull()
    }

    @Test
    @DisplayName("jti가 없는 예전 Refresh Token도 재발급과 로그아웃이 그대로 된다")
    fun oldTokenWithoutJtiStillWorks() {
        // given: jti를 넣기 전 형식({id, type})의 토큰을 DB와 쿠키에 둠
        val user = userRepository.findByEmail(email).orElseThrow()
        val oldToken = Ut.jwt.toString(
            secretKey,
            REFRESH_EXPIRE_MILLS,
            java.util.Map.of<String, Any>("id", requireNotNull(user.id), "type", "refresh"),
        )
        user.updateRefreshToken(oldToken)
        userRepository.save(user)

        // when & then: 재발급 성공
        mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1")
                .cookie(Cookie("refreshToken", oldToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())

        // when & then: 로그아웃 성공, DB에서 지워짐
        logout(oldToken).andExpectLogoutSuccess()
        assertThat(savedRefreshToken()).isNull()
    }

    companion object {
        private const val PASSWORD = "password1"

        // application.yml의 custom.cors.allowed-origins 기본값
        private const val ALLOWED_ORIGIN = "http://localhost:3000"

        private const val REFRESH_EXPIRE_MILLS = 1000L * 60 * 60 * 24 * 14
    }
}
