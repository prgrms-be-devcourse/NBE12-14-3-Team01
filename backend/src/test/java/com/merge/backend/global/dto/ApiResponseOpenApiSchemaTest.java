package com.merge.backend.global.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// springdoc이 만드는 Swagger 명세에서 공통 응답 Schema를 고정한다 (TECH-STACK TS-07, M0-6)
// Kotlin 전환 후 JSON이 같아도 Schema의 Field·타입·required가 달라지는지 잡기 위한 Test
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiResponseOpenApiSchemaTest {

    private static final String CONTEXT_PATH = "/api/v1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("모든 ApiResponse Schema는 success·code·message·data 순서의 Field를 가진다")
    void everyEnvelopeSchemaHasContractFields() throws Exception {
        Map<String, JsonNode> schemas = apiResponseSchemas();

        assertThat(schemas).isNotEmpty();
        schemas.forEach((name, schema) -> {
            assertThat(schema.get("type").asString()).as(name).isEqualTo("object");
            assertThat(fieldNames(schema.get("properties")))
                .as(name)
                .containsExactly("success", "code", "message", "data");

            JsonNode properties = schema.get("properties");
            assertThat(properties.get("success").get("type").asString()).as(name)
                .isEqualTo("boolean");
            assertThat(properties.get("code").get("type").asString()).as(name)
                .isEqualTo("string");
            assertThat(properties.get("message").get("type").asString()).as(name)
                .isEqualTo("string");
        });
    }

    @Test
    @DisplayName("ApiResponse Schema에는 required Field가 없다")
    void envelopeSchemaHasNoRequiredFields() throws Exception {
        apiResponseSchemas().forEach((name, schema) ->
            assertThat(schema.has("required")).as(name).isFalse()
        );
    }

    @Test
    @DisplayName("data의 Schema는 응답 타입을 따른다: 단건은 $ref, 목록은 array, Void는 빈 Schema")
    void dataSchemaFollowsResponseType() throws Exception {
        Map<String, JsonNode> schemas = apiResponseSchemas();

        assertThat(data(schemas, "ApiResponseWorkplaceCreateResponse").get("$ref").asString())
            .isEqualTo("#/components/schemas/WorkplaceCreateResponse");

        JsonNode listData = data(schemas, "ApiResponseListMyWorkplaceResponse");
        assertThat(listData.get("type").asString()).isEqualTo("array");
        assertThat(listData.get("items").get("$ref").asString())
            .isEqualTo("#/components/schemas/MyWorkplaceResponse");

        assertThat(fieldNames(data(schemas, "ApiResponseVoid"))).isEmpty();
    }

    private Map<String, JsonNode> apiResponseSchemas() throws Exception {
        String json = mvc.perform(get(CONTEXT_PATH + "/v3/api-docs").contextPath(CONTEXT_PATH))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode schemas = objectMapper.readTree(json).get("components").get("schemas");

        Map<String, JsonNode> result = new LinkedHashMap<>();
        schemas.properties().forEach(entry -> {
            if (entry.getKey().startsWith("ApiResponse")) {
                result.put(entry.getKey(), entry.getValue());
            }
        });
        return result;
    }

    private static JsonNode data(Map<String, JsonNode> schemas, String name) {
        assertThat(schemas).containsKey(name);
        return schemas.get(name).get("properties").get("data");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }
}
