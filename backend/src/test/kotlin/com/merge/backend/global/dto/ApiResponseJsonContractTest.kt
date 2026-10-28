package com.merge.backend.global.dto

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.JsonNodeType

/**
 * 공통 응답 [ApiResponse]의 JSON 형식 고정 (현재 Java 동작 기준).
 * 애플리케이션이 실제로 쓰는 Jackson Mapper Bean으로 직렬화한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiResponseJsonContractTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @DisplayName("성공 응답은 success·code·message·data 네 Field로 직렬화된다")
    fun successResponseSerializesFourFields() {
        // given
        val response = ApiResponse.success("200", "성공", mapOf("value" to "x"))

        // when
        val json = objectMapper.writeValueAsString(response)
        val root = objectMapper.readTree(json)

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(root.get("success").nodeType).isEqualTo(JsonNodeType.BOOLEAN)
        assertThat(JsonPath.read<Boolean>(json, "$.success")).isTrue()
        assertThat(root.get("code").nodeType).isEqualTo(JsonNodeType.STRING)
        assertThat(JsonPath.read<String>(json, "$.code")).isEqualTo("200")
        assertThat(root.get("message").nodeType).isEqualTo(JsonNodeType.STRING)
        assertThat(JsonPath.read<String>(json, "$.message")).isEqualTo("성공")
        assertThat(root.get("data").nodeType).isEqualTo(JsonNodeType.OBJECT)
        assertThat(JsonPath.read<String>(json, "$.data.value")).isEqualTo("x")
    }

    @Test
    @DisplayName("data 없는 성공 응답도 data Field를 null로 내보낸다")
    fun successResponseWithoutDataKeepsNullDataField() {
        // given
        val response = ApiResponse.success("200", "성공")

        // when
        val root = objectMapper.readTree(objectMapper.writeValueAsString(response))

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(root.get("data").nodeType).isEqualTo(JsonNodeType.NULL)
    }

    @Test
    @DisplayName("data 없는 오류 응답은 success false와 null data Field로 직렬화된다")
    fun errorResponseWithoutDataKeepsNullDataField() {
        // given
        val response = ApiResponse.error("TEST-001", "오류")

        // when
        val json = objectMapper.writeValueAsString(response)
        val root = objectMapper.readTree(json)

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(root.get("success").nodeType).isEqualTo(JsonNodeType.BOOLEAN)
        assertThat(JsonPath.read<Boolean>(json, "$.success")).isFalse()
        assertThat(root.get("data").nodeType).isEqualTo(JsonNodeType.NULL)
    }

    private fun fieldNames(node: JsonNode): Set<String> =
        node.properties().map { it.key }.toSet()
}
