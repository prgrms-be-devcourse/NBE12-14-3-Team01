package com.merge.backend.domain.user.controller

import com.jayway.jsonpath.JsonPath
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.standard.Ut
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiV1UserControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    // 테스트마다 가입된 유저 한 명으로 시작
    @BeforeEach
    fun setUp() {
        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL", "password": "$PASSWORD", "name": "유저1"}
                    """.trimIndent()))
            .andExpect(status().isCreated())
    }

    // 실제 DB에 남으니까 테스트마다 지움
    @AfterEach
    fun tearDown() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete)
    }

    @Test
    @DisplayName("로그인하면 accessToken을 주고 refreshToken을 HttpOnly 쿠키와 DB에 저장한다")
    fun test1() {
        val result = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.email").value(EMAIL))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(cookie().httpOnly("refreshToken", true))
            .andReturn()

        val refreshToken = requireNotNull(result.response.getCookie("refreshToken")).value
        val user = userRepository.findByEmail(EMAIL).orElseThrow()
        assertThat(user.refreshToken).isEqualTo(refreshToken)
    }

    @Test
    @DisplayName("로그인에서 받은 accessToken으로 내 정보를 조회하면 200과 내 정보를 반환한다")
    fun test2() {
        val loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andReturn()
        val accessToken: String = JsonPath.read(
            loginResult.response.contentAsString, "$.data.accessToken")
        val userId = userRepository.findByEmail(EMAIL).orElseThrow().id

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(userId))
            .andExpect(jsonPath("$.data.email").value(EMAIL))
            .andExpect(jsonPath("$.data.name").value("유저1"))
    }

    @Test
    @DisplayName("로그인에서 받은 refreshToken 쿠키로 재발급하면 200과 쓸 수 있는 새 accessToken을 반환한다")
    fun test3() {
        val loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andReturn()
        val refreshCookie = requireNotNull(loginResult.response.getCookie("refreshToken"))

        val refreshResult = mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1")
                .cookie(refreshCookie))
            .andExpect(status().isOk())
            .andReturn()
        val newAccessToken: String = JsonPath.read(
            refreshResult.response.contentAsString, "$.data.accessToken")

        // 새 accessToken으로 실제 API를 쓸 수 있는지
        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer " + newAccessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value(EMAIL))
    }

    @Test
    @DisplayName("refreshToken을 accessToken 자리에 넣어 요청하면 401과 AUTH-006을 반환한다")
    fun test4() {
        val loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andReturn()
        val refreshToken = requireNotNull(loginResult.response.getCookie("refreshToken")).value

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer " + refreshToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-006"))
    }

    @Test
    @DisplayName("accessToken을 refreshToken 쿠키 자리에 넣어 재발급하면 401과 AUTH-003을 반환한다")
    fun test5() {
        val loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andReturn()
        val accessToken: String = JsonPath.read(
            loginResult.response.contentAsString, "$.data.accessToken")

        mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1")
                .cookie(Cookie("refreshToken", accessToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-003"))
    }

    @Test
    @DisplayName("내용은 맞지만 다른 비밀키로 서명한 토큰으로 요청하면 401과 AUTH-006을 반환한다")
    fun test6() {
        val user = userRepository.findByEmail(EMAIL).orElseThrow()
        // 진짜 유저 정보로 만들되 서버가 모르는 키로 서명 (위조 토큰)
        val forgedToken = Ut.jwt.toString(
            "attacker-secret-key-attacker-secret-key-1234",
            1000L * 60 * 10,
            java.util.Map.of<String, Any>("id", user.id, "email", user.email, "name", user.name)
        )

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer " + forgedToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-006"))
    }

    @Test
    @DisplayName("회원가입 요청에 email이 없으면 400과 COM-001, 틀린 필드 정보를 반환한다")
    fun test7() {
        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"password": "$PASSWORD", "name": "유저2"}
                    """.trimIndent()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("email"))
    }

    @Test
    @DisplayName("회원가입하면 비밀번호 원문이 아니라 BCrypt 해시가 DB에 저장된다")
    fun test8() {
        val passwordHash = userRepository.findByEmail(EMAIL).orElseThrow().passwordHash

        assertThat(passwordHash).isNotEqualTo(PASSWORD)
        // 서버 설정과 별개로 새 BCrypt로 확인해야 암호화를 빼먹은 경우도 잡음
        assertThat(BCryptPasswordEncoder().matches(PASSWORD, passwordHash)).isTrue()
    }

    @Test
    @DisplayName("로그인 요청에 email이 없으면 400과 COM-001, 틀린 필드 정보를 반환한다")
    fun test9() {
        mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("email"))
    }

    @Test
    @DisplayName("로그인 요청에 password가 없으면 400과 COM-001, 틀린 필드 정보를 반환한다")
    fun test10() {
        mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL"}
                    """.trimIndent()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("password"))
    }

    @Test
    @DisplayName("회원가입 요청의 email 형식이 틀리면 400과 COM-001, 틀린 필드 정보를 반환한다")
    fun test11() {
        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "bad", "password": "$PASSWORD", "name": "유저2"}
                    """.trimIndent()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("email"))
    }

    @Test
    @DisplayName("로그인 요청의 email 형식이 틀리면 400과 COM-001, 틀린 필드 정보를 반환한다")
    fun test12() {
        mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "bad", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("email"))
    }

    @Test
    @DisplayName("로그인 응답 data는 user 다음에 accessToken 순서로 내려간다")
    fun test13() {
        val result = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andReturn()
        val body = result.response.contentAsString

        val userIndex = body.indexOf("\"user\"")
        val accessTokenIndex = body.indexOf("\"accessToken\"")
        assertThat(userIndex).isNotNegative()
        assertThat(userIndex).isLessThan(accessTokenIndex)
    }

    @Test
    @DisplayName("로그인에서 받은 accessToken의 유효 시간은 10분이다")
    fun test14() {
        val result = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andReturn()
        val accessToken: String = JsonPath.read(
            result.response.contentAsString, "$.data.accessToken")

        // 서명 확인 없이 토큰 가운데 부분(내용)만 읽음
        // Kotlin split은 정규식이 아니라 글자 그대로 나눔 (Java의 "\\."와 같음)
        val payload = String(Base64.getUrlDecoder().decode(accessToken.split(".")[1]))
        val issuedAt: Number = JsonPath.read(payload, "$.iat")
        val expiresAt: Number = JsonPath.read(payload, "$.exp")

        assertThat(expiresAt.toLong() - issuedAt.toLong()).isEqualTo(60L * 10)
    }

    @Test
    @DisplayName("로그인·재발급·내 정보 조회에 성공하면 각각 정해진 메시지를 반환한다")
    fun test15() {
        val loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("로그인에 성공했습니다"))
            .andReturn()
        val accessToken: String = JsonPath.read(
            loginResult.response.contentAsString, "$.data.accessToken")
        val refreshCookie = requireNotNull(loginResult.response.getCookie("refreshToken"))

        mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1")
                .cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Access Token이 재발급되었습니다"))

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("현재 사용자 정보를 조회했습니다"))
    }

    @Test
    @DisplayName("이메일 중복, 재발급 실패, 헤더 형식 오류, 잘못된 토큰은 각각 정해진 메시지를 반환한다")
    fun test16() {
        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$EMAIL", "password": "$PASSWORD", "name": "유저1"}
                    """.trimIndent()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AUTH-001"))
            .andExpect(jsonPath("$.message").value("이미 사용중인 이메일입니다"))

        mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-003"))
            .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다"))

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Basic abc123"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"))
            .andExpect(jsonPath("$.message").value("Authorization 헤더가 Bearer 형식이 아닙니다."))

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-006"))
            .andExpect(jsonPath("$.message").value("Access Token이 유효하지 않습니다."))
    }

    companion object {
        private const val EMAIL = "user1@test.com"
        private const val PASSWORD = "password1"
    }
}
