package com.merge.backend.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 처리하지 않은 예외가 실제 HTTP 흐름에서 500 COM-500 공통 응답으로 나가는지 고정한다 (CB-COM-04)
// 예외를 던지는 Controller는 이 Test에서만 등록한다 (중첩 Class라 다른 Test의 Component Scan에서 제외됨)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(UnhandledExceptionHttpContractTest.ThrowingController.class)
class UnhandledExceptionHttpContractTest {

    private static final String CONTEXT_PATH = "/api/v1";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("처리하지 않은 예외는 500과 COM-500, data null 공통 응답으로 바뀐다")
    void unhandledExceptionBecomesCom500() throws Exception {
        String body = mvc.perform(get(CONTEXT_PATH + "/test/unhandled-exception")
                .contextPath(CONTEXT_PATH)
                .with(user("contract-tester")))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).isEqualTo(
            "{\"success\":false,\"code\":\"COM-500\","
                + "\"message\":\"서버 내부 오류가 발생했습니다.\",\"data\":null}"
        );
    }

    @Test
    @DisplayName("예외 메시지는 응답에 노출되지 않는다")
    void exceptionMessageIsNotExposed() throws Exception {
        String body = mvc.perform(get(CONTEXT_PATH + "/test/unhandled-exception")
                .contextPath(CONTEXT_PATH)
                .with(user("contract-tester")))
            .andExpect(status().isInternalServerError())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain(ThrowingController.SECRET_MESSAGE);
    }

    @RestController
    static class ThrowingController {

        static final String SECRET_MESSAGE = "internal-detail-should-not-leak";

        @GetMapping("/test/unhandled-exception")
        void throwUnhandled() {
            throw new IllegalStateException(SECRET_MESSAGE);
        }
    }
}
