package com.merge.backend.global.security

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CustomAuthenticationFilterTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    @DisplayName("토큰 없이 요청하면 401과 AUTH-004를 반환한다")
    fun test1() {
        // 실제 주소처럼 /api/v1을 붙여야 인증 필터가 검사함
        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-004"))
    }

    @Test
    @DisplayName("Authorization 헤더가 Bearer로 시작하지 않으면 401과 AUTH-005를 반환한다")
    fun test2() {
        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Basic abc123"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"))
    }

    @Test
    @DisplayName("Bearer 뒤의 토큰이 유효하지 않으면 401과 AUTH-006을 반환한다")
    fun test3() {
        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-006"))
    }

    @Test
    @DisplayName("재발급 요청은 Authorization 헤더가 Bearer 형식이 아니어도 401과 AUTH-003을 반환한다")
    fun test4() {
        // 재발급은 필터가 검사하지 않는 주소라서 헤더 대신 쿠키 없음(AUTH-003)으로 응답
        mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1")
                .header("Authorization", "Basic abc123"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-003"))
    }

    @Test
    @DisplayName("Authorization 헤더에 Bearer만 있고 토큰이 없으면 401과 AUTH-005를 반환한다")
    fun test5() {
        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"))
    }
}
