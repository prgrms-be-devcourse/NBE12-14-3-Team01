package com.merge.backend.global.dto

import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

// springdoc이 만드는 Swagger 명세에서 공통 응답 Schema를 고정한다 (TECH-STACK TS-07, M0-6)
// Kotlin 전환 후 JSON이 같아도 Schema의 Field·타입·required가 달라지는지 잡기 위한 Test
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiResponseOpenApiSchemaTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @DisplayName("모든 ApiResponse Schema는 success·code·message·data 순서의 Field를 가진다")
    fun everyEnvelopeSchemaHasContractFields() {
        val schemas = apiResponseSchemas()

        assertThat(schemas).isNotEmpty()
        schemas.forEach { (name, schema) ->
            assertThat(schema.get("type").asString()).`as`(name).isEqualTo("object")
            assertThat(fieldNames(schema.get("properties")))
                .`as`(name)
                .containsExactly("success", "code", "message", "data")

            val properties = schema.get("properties")
            assertThat(properties.get("success").get("type").asString()).`as`(name)
                .isEqualTo("boolean")
            assertThat(properties.get("code").get("type").asString()).`as`(name)
                .isEqualTo("string")
            assertThat(properties.get("message").get("type").asString()).`as`(name)
                .isEqualTo("string")
        }
    }

    @Test
    @DisplayName("ApiResponse Schema에는 required Field가 없다")
    fun envelopeSchemaHasNoRequiredFields() {
        apiResponseSchemas().forEach { (name, schema) ->
            assertThat(schema.has("required")).`as`(name).isFalse()
        }
    }

    @Test
    @DisplayName("data의 Schema는 응답 타입을 따른다: 단건은 \$ref, 목록은 array, Void는 빈 Schema")
    fun dataSchemaFollowsResponseType() {
        val schemas = apiResponseSchemas()

        assertThat(data(schemas, "ApiResponseWorkplaceCreateResponse").get("\$ref").asString())
            .isEqualTo("#/components/schemas/WorkplaceCreateResponse")

        val listData = data(schemas, "ApiResponseListMyWorkplaceResponse")
        assertThat(listData.get("type").asString()).isEqualTo("array")
        assertThat(listData.get("items").get("\$ref").asString())
            .isEqualTo("#/components/schemas/MyWorkplaceResponse")

        assertThat(fieldNames(data(schemas, "ApiResponseVoid"))).isEmpty()
    }

    private fun apiResponseSchemas(): Map<String, JsonNode> {
        val json = mvc.perform(get("$CONTEXT_PATH/v3/api-docs").contextPath(CONTEXT_PATH))
            .andExpect(status().isOk())
            .andReturn()
            .response
            .getContentAsString(StandardCharsets.UTF_8)

        val schemas = objectMapper.readTree(json).get("components").get("schemas")

        val result = LinkedHashMap<String, JsonNode>()
        schemas.properties().forEach { (key, value) ->
            if (key.startsWith("ApiResponse")) {
                result[key] = value
            }
        }
        return result
    }

    private fun data(schemas: Map<String, JsonNode>, name: String): JsonNode {
        assertThat(schemas).containsKey(name)
        return schemas.getValue(name).get("properties").get("data")
    }

    private fun fieldNames(node: JsonNode): List<String> =
        node.properties().map { it.key }

    companion object {
        private const val CONTEXT_PATH = "/api/v1"
    }
}
