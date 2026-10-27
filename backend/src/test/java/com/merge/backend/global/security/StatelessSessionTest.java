package com.merge.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

// MockMvc는 세션 쿠키를 응답 헤더로 보여 주지 않아서 임의 포트의 실제 서버로 확인
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StatelessSessionTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("토큰 없이 요청해 401이 나도 세션 쿠키(JSESSIONID)를 만들지 않는다")
    void unauthorizedRequest_noSessionCookie() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/api/v1/auth/me")).GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().allValues("Set-Cookie"))
            .noneMatch(value -> value.startsWith("JSESSIONID"));
    }
}
