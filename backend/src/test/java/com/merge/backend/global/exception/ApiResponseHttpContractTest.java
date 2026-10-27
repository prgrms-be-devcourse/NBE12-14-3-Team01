package com.merge.backend.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeType;

/**
 * 실제 HTTP 응답의 공통 형식 고정 (현재 Java 동작 기준).
 * Context Path(/api/v1)를 포함해 호출해 Security Filter와 전역 예외 처리기를 거친다.
 * 회원가입은 고유 이메일을 쓰고 Test가 끝나면 Rollback한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ApiResponseHttpContractTest {

    private static final String CONTEXT_PATH = "/api/v1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("회원가입 성공 응답은 201과 공통 형식, data의 id·email·name을 돌려준다")
    void signupSuccessResponseContract() throws Exception {
        // given
        String email = uniqueEmail();

        // when
        MvcResult result = mvc.perform(post(CONTEXT_PATH + "/auth/signup")
                .contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "password1", "name": "계약테스트"}
                    """.formatted(email)))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();
        String json = body(result);
        JsonNode root = objectMapper.readTree(json);

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data");
        assertThat((Boolean) JsonPath.read(json, "$.success")).isTrue();
        assertThat((String) JsonPath.read(json, "$.code")).isEqualTo("201");
        assertThat((String) JsonPath.read(json, "$.message")).isEqualTo("회원가입이 완료되었습니다.");
        JsonNode data = root.get("data");
        assertThat(fieldNames(data)).containsExactlyInAnyOrder("id", "email", "name");
        assertThat(data.get("id").getNodeType()).isEqualTo(JsonNodeType.NUMBER);
        assertThat((String) JsonPath.read(json, "$.data.email")).isEqualTo(email);
        assertThat((String) JsonPath.read(json, "$.data.name")).isEqualTo("계약테스트");
    }

    @Test
    @DisplayName("업무 오류 응답은 ErrorCode의 HTTP 상태와 공통 형식, null data를 돌려준다")
    void businessErrorResponseContract() throws Exception {
        // given: 가입하지 않은 이메일로 로그인 → BusinessException(INVALID_CREDENTIALS)

        // when
        MvcResult result = mvc.perform(post(CONTEXT_PATH + "/auth/login")
                .contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "password": "password1"}
                    """.formatted(uniqueEmail())))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();
        String json = body(result);
        JsonNode root = objectMapper.readTree(json);

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data");
        assertThat((Boolean) JsonPath.read(json, "$.success")).isFalse();
        assertThat((String) JsonPath.read(json, "$.code")).isEqualTo("AUTH-002");
        assertThat((String) JsonPath.read(json, "$.message"))
            .isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다");
        assertThat(root.get("data").getNodeType()).isEqualTo(JsonNodeType.NULL);
    }

    @Test
    @DisplayName("요청 값 검증 실패는 400 COM-001과 fieldErrors 목록을 돌려준다")
    void validationErrorResponseContract() throws Exception {
        // given: JSON 형식은 올바르고 값만 비어 있음 → @Valid 검증 실패 경로

        // when
        MvcResult result = mvc.perform(post(CONTEXT_PATH + "/auth/signup")
                .contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "", "password": "", "name": ""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();
        String json = body(result);
        JsonNode root = objectMapper.readTree(json);

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data");
        assertThat((Boolean) JsonPath.read(json, "$.success")).isFalse();
        assertThat((String) JsonPath.read(json, "$.code")).isEqualTo("COM-001");
        assertThat((String) JsonPath.read(json, "$.message")).isEqualTo("요청 값이 올바르지 않습니다.");

        JsonNode fieldErrors = root.get("data").get("fieldErrors");
        assertThat(fieldNames(root.get("data"))).containsExactly("fieldErrors");
        assertThat(fieldErrors.getNodeType()).isEqualTo(JsonNodeType.ARRAY);
        Set<String> fields = new HashSet<>();
        for (JsonNode error : fieldErrors) {
            assertThat(fieldNames(error)).containsExactlyInAnyOrder("field", "message");
            assertThat(error.get("field").getNodeType()).isEqualTo(JsonNodeType.STRING);
            assertThat(error.get("message").getNodeType()).isEqualTo(JsonNodeType.STRING);
            fields.add(objectMapper.treeToValue(error.get("field"), String.class));
        }
        // 순서와 문구는 Locale·Validator에 따라 달라질 수 있어 Field 이름 집합만 고정
        assertThat(fields).containsExactlyInAnyOrder("email", "password", "name");
    }

    private static String uniqueEmail() {
        return "contract-" + UUID.randomUUID() + "@test.com";
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }
}
