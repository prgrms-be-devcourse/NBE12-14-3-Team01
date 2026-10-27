package com.merge.backend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.user.repository.UserRepository;
import com.merge.backend.standard.Ut;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiV1UserControllerTest {

    private static final String EMAIL = "user1@test.com";
    private static final String PASSWORD = "password1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    // 테스트마다 가입된 유저 한 명으로 시작
    @BeforeEach
    void setUp() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s", "name": "유저1"}
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isCreated());
    }

    // 실제 DB에 남으니까 테스트마다 지움
    @AfterEach
    void tearDown() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("로그인하면 accessToken을 주고 refreshToken을 HttpOnly 쿠키와 DB에 저장한다")
    void test1() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s"}
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.email").value(EMAIL))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(cookie().httpOnly("refreshToken", true))
            .andReturn();

        String refreshToken = result.getResponse().getCookie("refreshToken").getValue();
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getRefreshToken()).isEqualTo(refreshToken);
    }

    @Test
    @DisplayName("로그인에서 받은 accessToken으로 내 정보를 조회하면 200과 내 정보를 반환한다")
    void test2() throws Exception {
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s"}
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        String accessToken = JsonPath.read(
            loginResult.getResponse().getContentAsString(), "$.data.accessToken");
        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(userId))
            .andExpect(jsonPath("$.data.email").value(EMAIL))
            .andExpect(jsonPath("$.data.name").value("유저1"));
    }

    @Test
    @DisplayName("로그인에서 받은 refreshToken 쿠키로 재발급하면 200과 쓸 수 있는 새 accessToken을 반환한다")
    void test3() throws Exception {
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s"}
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

        MvcResult refreshResult = mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1")
                .cookie(refreshCookie))
            .andExpect(status().isOk())
            .andReturn();
        String newAccessToken = JsonPath.read(
            refreshResult.getResponse().getContentAsString(), "$.data.accessToken");

        // 새 accessToken으로 실제 API를 쓸 수 있는지
        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer " + newAccessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    @Test
    @DisplayName("refreshToken을 accessToken 자리에 넣어 요청하면 401과 AUTH-006을 반환한다")
    void test4() throws Exception {
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s"}
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        String refreshToken = loginResult.getResponse().getCookie("refreshToken").getValue();

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer " + refreshToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-006"));
    }

    @Test
    @DisplayName("accessToken을 refreshToken 쿠키 자리에 넣어 재발급하면 401과 AUTH-003을 반환한다")
    void test5() throws Exception {
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s"}
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        String accessToken = JsonPath.read(
            loginResult.getResponse().getContentAsString(), "$.data.accessToken");

        mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1")
                .cookie(new Cookie("refreshToken", accessToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    @DisplayName("내용은 맞지만 다른 비밀키로 서명한 토큰으로 요청하면 401과 AUTH-006을 반환한다")
    void test6() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        // 진짜 유저 정보로 만들되 서버가 모르는 키로 서명 (위조 토큰)
        String forgedToken = Ut.jwt.toString(
            "attacker-secret-key-attacker-secret-key-1234",
            1000L * 60 * 10,
            Map.of("id", user.getId(), "email", user.getEmail(), "name", user.getName())
        );

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer " + forgedToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-006"));
    }

    @Test
    @DisplayName("회원가입 요청에 email이 없으면 400과 COM-001, 틀린 필드 정보를 반환한다")
    void test7() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"password": "%s", "name": "유저2"}
                    """.formatted(PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("email"));
    }

    @Test
    @DisplayName("회원가입하면 비밀번호 원문이 아니라 BCrypt 해시가 DB에 저장된다")
    void test8() {
        String passwordHash = userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash();

        assertThat(passwordHash).isNotEqualTo(PASSWORD);
        // 서버 설정과 별개로 새 BCrypt로 확인해야 암호화를 빼먹은 경우도 잡음
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, passwordHash)).isTrue();
    }

    @Test
    @DisplayName("로그인 요청에 email이 없으면 400과 COM-001, 틀린 필드 정보를 반환한다")
    void test9() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"password": "%s"}
                    """.formatted(PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("email"));
    }

    @Test
    @DisplayName("로그인 요청에 password가 없으면 400과 COM-001, 틀린 필드 정보를 반환한다")
    void test10() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s"}
                    """.formatted(EMAIL)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("password"));
    }

    @Test
    @DisplayName("회원가입 요청의 email 형식이 틀리면 400과 COM-001, 틀린 필드 정보를 반환한다")
    void test11() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "bad", "password": "%s", "name": "유저2"}
                    """.formatted(PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("email"));
    }

    @Test
    @DisplayName("로그인 요청의 email 형식이 틀리면 400과 COM-001, 틀린 필드 정보를 반환한다")
    void test12() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "bad", "password": "%s"}
                    """.formatted(PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("email"));
    }

    @Test
    @DisplayName("로그인 응답 data는 user 다음에 accessToken 순서로 내려간다")
    void test13() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s"}
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        String body = result.getResponse().getContentAsString();

        int userIndex = body.indexOf("\"user\"");
        int accessTokenIndex = body.indexOf("\"accessToken\"");
        assertThat(userIndex).isNotNegative();
        assertThat(userIndex).isLessThan(accessTokenIndex);
    }

    @Test
    @DisplayName("로그인에서 받은 accessToken의 유효 시간은 10분이다")
    void test14() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s"}
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        String accessToken = JsonPath.read(
            result.getResponse().getContentAsString(), "$.data.accessToken");

        // 서명 확인 없이 토큰 가운데 부분(내용)만 읽음
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        Number issuedAt = JsonPath.read(payload, "$.iat");
        Number expiresAt = JsonPath.read(payload, "$.exp");

        assertThat(expiresAt.longValue() - issuedAt.longValue()).isEqualTo(60L * 10);
    }

    @Test
    @DisplayName("로그인·재발급·내 정보 조회에 성공하면 각각 정해진 메시지를 반환한다")
    void test15() throws Exception {
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s"}
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("로그인에 성공했습니다"))
            .andReturn();
        String accessToken = JsonPath.read(
            loginResult.getResponse().getContentAsString(), "$.data.accessToken");
        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

        mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1")
                .cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Access Token이 재발급되었습니다"));

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("현재 사용자 정보를 조회했습니다"));
    }

    @Test
    @DisplayName("이메일 중복, 재발급 실패, 헤더 형식 오류, 잘못된 토큰은 각각 정해진 메시지를 반환한다")
    void test16() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "%s", "name": "유저1"}
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AUTH-001"))
            .andExpect(jsonPath("$.message").value("이미 사용중인 이메일입니다"));

        mvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-003"))
            .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다"));

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Basic abc123"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"))
            .andExpect(jsonPath("$.message").value("Authorization 헤더가 Bearer 형식이 아닙니다."));

        mvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-006"))
            .andExpect(jsonPath("$.message").value("Access Token이 유효하지 않습니다."));
    }
}
