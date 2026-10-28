package com.merge.backend.global.exception

import com.jayway.jsonpath.JsonPath
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.JsonNodeType

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

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @DisplayName("회원가입 성공 응답은 201과 공통 형식, data의 id·email·name을 돌려준다")
    fun signupSuccessResponseContract() {
        // given
        val email = uniqueEmail()

        // when
        val result = mvc.perform(
            post("$CONTEXT_PATH/auth/signup")
                .contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "password": "password1", "name": "계약테스트"}
                    """.trimIndent().format(email)
                )
        )
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn()
        val json = body(result)
        val root = objectMapper.readTree(json)

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(JsonPath.read<Boolean>(json, "$.success")).isTrue()
        assertThat(JsonPath.read<String>(json, "$.code")).isEqualTo("201")
        assertThat(JsonPath.read<String>(json, "$.message")).isEqualTo("회원가입이 완료되었습니다.")
        val data = root.get("data")
        assertThat(fieldNames(data)).containsExactlyInAnyOrder("id", "email", "name")
        assertThat(data.get("id").nodeType).isEqualTo(JsonNodeType.NUMBER)
        assertThat(JsonPath.read<String>(json, "$.data.email")).isEqualTo(email)
        assertThat(JsonPath.read<String>(json, "$.data.name")).isEqualTo("계약테스트")
    }

    @Test
    @DisplayName("업무 오류 응답은 ErrorCode의 HTTP 상태와 공통 형식, null data를 돌려준다")
    fun businessErrorResponseContract() {
        // given: 가입하지 않은 이메일로 로그인 → BusinessException(INVALID_CREDENTIALS)

        // when
        val result = mvc.perform(
            post("$CONTEXT_PATH/auth/login")
                .contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "password": "password1"}
                    """.trimIndent().format(uniqueEmail())
                )
        )
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn()
        val json = body(result)
        val root = objectMapper.readTree(json)

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(JsonPath.read<Boolean>(json, "$.success")).isFalse()
        assertThat(JsonPath.read<String>(json, "$.code")).isEqualTo("AUTH-002")
        assertThat(JsonPath.read<String>(json, "$.message"))
            .isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다")
        assertThat(root.get("data").nodeType).isEqualTo(JsonNodeType.NULL)
    }

    @Test
    @DisplayName("요청 값 검증 실패는 400 COM-001과 fieldErrors 목록을 돌려준다")
    fun validationErrorResponseContract() {
        // given: JSON 형식은 올바르고 값만 비어 있음 → @Valid 검증 실패 경로

        // when
        val result = mvc.perform(
            post("$CONTEXT_PATH/auth/signup")
                .contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "", "password": "", "name": ""}
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn()
        val json = body(result)
        val root = objectMapper.readTree(json)

        // then
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(JsonPath.read<Boolean>(json, "$.success")).isFalse()
        assertThat(JsonPath.read<String>(json, "$.code")).isEqualTo("COM-001")
        assertThat(JsonPath.read<String>(json, "$.message")).isEqualTo("요청 값이 올바르지 않습니다.")

        val fieldErrors = root.get("data").get("fieldErrors")
        assertThat(fieldNames(root.get("data"))).containsExactly("fieldErrors")
        assertThat(fieldErrors.nodeType).isEqualTo(JsonNodeType.ARRAY)
        val fields = HashSet<String>()
        for (error in fieldErrors) {
            assertThat(fieldNames(error)).containsExactlyInAnyOrder("field", "message")
            assertThat(error.get("field").nodeType).isEqualTo(JsonNodeType.STRING)
            assertThat(error.get("message").nodeType).isEqualTo(JsonNodeType.STRING)
            fields.add(objectMapper.treeToValue(error.get("field"), String::class.java))
        }
        // 순서와 문구는 Locale·Validator에 따라 달라질 수 있어 Field 이름 집합만 고정
        assertThat(fields).containsExactlyInAnyOrder("email", "password", "name")
    }

    private fun uniqueEmail(): String = "contract-" + UUID.randomUUID() + "@test.com"

    private fun body(result: MvcResult): String =
        result.response.getContentAsString(StandardCharsets.UTF_8)

    private fun fieldNames(node: JsonNode): Set<String> =
        node.properties().map { it.key }.toSet()

    companion object {
        private const val CONTEXT_PATH = "/api/v1"
    }
}
