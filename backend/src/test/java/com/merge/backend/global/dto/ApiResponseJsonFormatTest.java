package com.merge.backend.global.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.merge.backend.global.dto.ValidationErrorResponse.FieldErrorDetail;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

// ApiResponseJsonContractTest는 Field 집합·타입을 고정하고, 이 Test는 그 외의 형식을 고정한다 (CB-COM-02)
// Field 순서(@JsonPropertyOrder, Jackson 3 기본값은 알파벳 순), message "", success overload 선택,
// 공통 data DTO의 JSON을 실제 응답에 쓰이는 Spring ObjectMapper로 문자열 그대로 비교한다
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiResponseJsonFormatTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("데이터가 있는 성공 응답은 success·code·message·data 순서로 직렬화된다")
    void successWithData() {
        ApiResponse<Map<String, Long>> response =
            ApiResponse.success("200", "조회했습니다.", Map.of("id", 1L));

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
            "{\"success\":true,\"code\":\"200\",\"message\":\"조회했습니다.\",\"data\":{\"id\":1}}"
        );
    }

    @Test
    @DisplayName("메시지 없이 데이터만 넘기면 message는 빈 문자열로 직렬화된다")
    void successWithoutMessage() {
        ApiResponse<List<Integer>> response = ApiResponse.success("200", List.of(1, 2));

        assertThat(response.getMessage()).isEmpty();
        assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
            "{\"success\":true,\"code\":\"200\",\"message\":\"\",\"data\":[1,2]}"
        );
    }

    @Test
    @DisplayName("두 번째 인자가 String이면 data가 아닌 message로 쓰인다")
    void successWithStringSecondArgument() {
        ApiResponse<Void> response = ApiResponse.success("200", "처리했습니다.");

        assertThat(response.getMessage()).isEqualTo("처리했습니다.");
        assertThat(response.getData()).isNull();
        assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
            "{\"success\":true,\"code\":\"200\",\"message\":\"처리했습니다.\",\"data\":null}"
        );
    }

    @Test
    @DisplayName("에러 응답도 success·code·message·data 순서로 직렬화된다")
    void errorWithData() {
        ApiResponse<Map<String, Boolean>> response =
            ApiResponse.error("SFT-001", "확인이 필요합니다.", Map.of("flag", true));

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
            "{\"success\":false,\"code\":\"SFT-001\",\"message\":\"확인이 필요합니다.\","
                + "\"data\":{\"flag\":true}}"
        );
    }

    @Test
    @DisplayName("검증 오류 data는 fieldErrors 배열 안에 field·message로 직렬화된다")
    void validationErrorResponse() {
        ValidationErrorResponse data = new ValidationErrorResponse(
            List.of(new FieldErrorDetail("name", "이름은 필수입니다."))
        );

        assertThat(objectMapper.writeValueAsString(data)).isEqualTo(
            "{\"fieldErrors\":[{\"field\":\"name\",\"message\":\"이름은 필수입니다.\"}]}"
        );
    }

    @Test
    @DisplayName("확인 필요 data는 requiresConfirmation 하나로 직렬화된다")
    void confirmationRequiredResponse() {
        ConfirmationRequiredResponse data = new ConfirmationRequiredResponse(true);

        assertThat(objectMapper.writeValueAsString(data))
            .isEqualTo("{\"requiresConfirmation\":true}");
    }

    @Test
    @DisplayName("실제 HTTP 응답 본문도 success·code·message·data 순서로 나간다")
    void httpResponseKeepsFieldOrder() throws Exception {
        // 가입하지 않은 이메일로 로그인 → BusinessException(AUTH-002) → GlobalExceptionHandler
        String body = mvc.perform(post("/api/v1/auth/login")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"format-" + UUID.randomUUID() + "@test.com\", "
                    + "\"password\": \"password1\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).isEqualTo(
            "{\"success\":false,\"code\":\"AUTH-002\","
                + "\"message\":\"이메일 또는 비밀번호가 올바르지 않습니다\",\"data\":null}"
        );
    }
}
