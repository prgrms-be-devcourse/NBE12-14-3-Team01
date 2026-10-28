package com.merge.backend.global.security

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    @DisplayName("응답에 X-Frame-Options SAMEORIGIN 헤더를 붙인다")
    fun frameOptions_sameOrigin() {
        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1"))
            .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
    }

    @Test
    @DisplayName("허용한 프론트 주소의 사전 요청은 200과 쿠키 허용 헤더를 반환한다")
    fun corsPreflight_allowedOrigin() {
        mvc.perform(options("/api/v1/auth/login").contextPath("/api/v1")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
    }

    @Test
    @DisplayName("허용하지 않은 주소의 사전 요청은 403을 반환한다")
    fun corsPreflight_otherOrigin() {
        mvc.perform(options("/api/v1/auth/login").contextPath("/api/v1")
                .header("Origin", "http://evil.com")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden())
    }

    companion object {
        // test Profile은 허용 주소 기본값(http://localhost:3000)을 씀
        private const val ALLOWED_ORIGIN = "http://localhost:3000"
    }
}
