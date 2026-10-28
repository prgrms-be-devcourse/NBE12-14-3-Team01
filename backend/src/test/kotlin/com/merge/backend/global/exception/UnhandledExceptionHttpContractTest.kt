package com.merge.backend.global.exception

import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// 처리하지 않은 예외가 실제 HTTP 흐름에서 500 COM-500 공통 응답으로 나가는지 고정한다 (CB-COM-04)
// 예외를 던지는 Controller는 이 Test에서만 등록한다 (중첩 Class라 다른 Test의 Component Scan에서 제외됨)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(UnhandledExceptionHttpContractTest.ThrowingController::class)
class UnhandledExceptionHttpContractTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    @DisplayName("처리하지 않은 예외는 500과 COM-500, data null 공통 응답으로 바뀐다")
    fun unhandledExceptionBecomesCom500() {
        val body = mvc.perform(
            get("$CONTEXT_PATH/test/unhandled-exception")
                .contextPath(CONTEXT_PATH)
                .with(user("contract-tester"))
        )
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn()
            .response
            .getContentAsString(StandardCharsets.UTF_8)

        assertThat(body).isEqualTo(
            "{\"success\":false,\"code\":\"COM-500\"," +
                "\"message\":\"서버 내부 오류가 발생했습니다.\",\"data\":null}"
        )
    }

    @Test
    @DisplayName("예외 메시지는 응답에 노출되지 않는다")
    fun exceptionMessageIsNotExposed() {
        val body = mvc.perform(
            get("$CONTEXT_PATH/test/unhandled-exception")
                .contextPath(CONTEXT_PATH)
                .with(user("contract-tester"))
        )
            .andExpect(status().isInternalServerError())
            .andReturn()
            .response
            .getContentAsString(StandardCharsets.UTF_8)

        assertThat(body).doesNotContain(ThrowingController.SECRET_MESSAGE)
    }

    // Kotlin 중첩 class는 Java의 static 중첩 class와 같다
    @RestController
    class ThrowingController {

        @GetMapping("/test/unhandled-exception")
        fun throwUnhandled() {
            throw IllegalStateException(SECRET_MESSAGE)
        }

        companion object {
            const val SECRET_MESSAGE = "internal-detail-should-not-leak"
        }
    }

    companion object {
        private const val CONTEXT_PATH = "/api/v1"
    }
}
