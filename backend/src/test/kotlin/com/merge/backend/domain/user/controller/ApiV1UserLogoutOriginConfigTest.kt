package com.merge.backend.domain.user.controller

import com.merge.backend.domain.user.repository.UserRepository
import jakarta.servlet.http.Cookie
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// 운영 환경변수를 끝에 /를 붙이거나 대문자로 적은 경우
@SpringBootTest(properties = ["custom.cors.allowed-origins=https://app.example.com/, https://Shop.Example.com"])
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiV1UserLogoutOriginConfigTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    // 같은 DB를 다른 테스트와 같이 쓰므로 테스트마다 다른 이메일
    private lateinit var email: String

    @BeforeEach
    fun setUp() {
        email = "logout-origin-config-${UUID.randomUUID()}@test.com"

        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$email", "password": "$PASSWORD", "name": "설정유저"}
                    """.trimIndent()))
            .andExpect(status().isCreated())
    }

    // 실제 DB에 남으니까 테스트마다 지움
    @AfterEach
    fun tearDown() {
        userRepository.findByEmail(email).ifPresent(userRepository::delete)
    }

    private fun loginRefreshToken(): String {
        val result = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "$email", "password": "$PASSWORD"}
                    """.trimIndent()))
            .andExpect(status().isOk())
            .andReturn()
        return requireNotNull(result.response.getCookie("refreshToken")).value
    }

    @Test
    @DisplayName("허용 목록이 끝에 /가 붙거나 대문자로 적혀도 CORS를 통과한 Origin이면 로그아웃된다")
    fun logoutWithOriginWrittenDifferentlyInConfigSucceeds() {
        // 브라우저는 Origin을 소문자, 끝에 / 없이 보냄
        for (origin in listOf("https://app.example.com", "https://shop.example.com")) {
            // given
            val refreshToken = loginRefreshToken()

            // when
            val result = mvc.perform(post("/api/v1/auth/logout").contextPath("/api/v1")
                    .header("Origin", origin)
                    .cookie(Cookie("refreshToken", refreshToken)))

            // then: CORS도 통과(허가 헤더)하고 로그아웃도 처리됨
            result.andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다"))
            assertThat(userRepository.findByEmail(email).orElseThrow().refreshToken).isNull()
        }
    }

    companion object {
        private const val PASSWORD = "password1"
    }
}
