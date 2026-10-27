package com.merge.backend.global.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeType;

/**
 * 공통 응답 {@link ApiResponse}의 JSON 형식 고정 (현재 Java 동작 기준).
 * 애플리케이션이 실제로 쓰는 Jackson Mapper Bean으로 직렬화한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiResponseJsonContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("성공 응답은 success·code·message·data 네 Field로 직렬화된다")
    void successResponseSerializesFourFields() {
        // given
        ApiResponse<Map<String, String>> response =
            ApiResponse.success("200", "성공", Map.of("value", "x"));

        // when
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data");
        assertThat(root.get("success").getNodeType()).isEqualTo(JsonNodeType.BOOLEAN);
        assertThat((Boolean) JsonPath.read(json, "$.success")).isTrue();
        assertThat(root.get("code").getNodeType()).isEqualTo(JsonNodeType.STRING);
        assertThat((String) JsonPath.read(json, "$.code")).isEqualTo("200");
        assertThat(root.get("message").getNodeType()).isEqualTo(JsonNodeType.STRING);
        assertThat((String) JsonPath.read(json, "$.message")).isEqualTo("성공");
        assertThat(root.get("data").getNodeType()).isEqualTo(JsonNodeType.OBJECT);
        assertThat((String) JsonPath.read(json, "$.data.value")).isEqualTo("x");
    }

    @Test
    @DisplayName("data 없는 성공 응답도 data Field를 null로 내보낸다")
    void successResponseWithoutDataKeepsNullDataField() {
        // given
        ApiResponse<Void> response = ApiResponse.success("200", "성공");

        // when
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data");
        assertThat(root.get("data").getNodeType()).isEqualTo(JsonNodeType.NULL);
    }

    @Test
    @DisplayName("data 없는 오류 응답은 success false와 null data Field로 직렬화된다")
    void errorResponseWithoutDataKeepsNullDataField() {
        // given
        ApiResponse<Void> response = ApiResponse.error("TEST-001", "오류");

        // when
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data");
        assertThat(root.get("success").getNodeType()).isEqualTo(JsonNodeType.BOOLEAN);
        assertThat((Boolean) JsonPath.read(json, "$.success")).isFalse();
        assertThat(root.get("data").getNodeType()).isEqualTo(JsonNodeType.NULL);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }
}
