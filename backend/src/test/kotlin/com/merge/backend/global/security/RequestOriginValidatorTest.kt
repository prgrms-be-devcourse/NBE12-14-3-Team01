package com.merge.backend.global.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RequestOriginValidatorTest {

    private val validator = RequestOriginValidator(listOf(ALLOWED_ORIGIN))

    @Test
    @DisplayName("Origin이 있으면 Referer와 상관없이 Origin이 정확히 같을 때만 허용한다")
    fun isAllowedUsesOnlyOriginWhenPresent() {
        // CORS가 먼저 막지 못하는 경우에도 이 검사만으로 거부되는지
        assertThat(validator.isAllowed(ALLOWED_ORIGIN, null)).isTrue()
        assertThat(validator.isAllowed("http://evil.example", "$ALLOWED_ORIGIN/login")).isFalse()
        assertThat(validator.isAllowed("null", "$ALLOWED_ORIGIN/login")).isFalse()
        assertThat(validator.isAllowed("http://localhost:3001", null)).isFalse()
        assertThat(validator.isAllowed("https://localhost:3000", null)).isFalse()
    }

    @Test
    @DisplayName("Origin이 없으면 Referer의 출처(scheme, host, port)로 비교한다")
    fun isAllowedComparesRefererOriginWhenOriginMissing() {
        assertThat(validator.isAllowed(null, "$ALLOWED_ORIGIN/schedule?week=1")).isTrue()
        assertThat(validator.isAllowed("", "$ALLOWED_ORIGIN/")).isTrue()
        assertThat(validator.isAllowed(null, "http://evil.example/page")).isFalse()
        // 앞글자만 같은 주소는 다른 출처
        assertThat(validator.isAllowed(null, "http://localhost:30001/page")).isFalse()
        assertThat(validator.isAllowed(null, "http://localhost:3000@evil.example/page")).isFalse()
        assertThat(validator.isAllowed(null, "http://localhost/page")).isFalse()
    }

    @Test
    @DisplayName("Origin과 Referer가 둘 다 없거나 Referer를 읽을 수 없으면 거부한다")
    fun isAllowedRejectsMissingOrBrokenHeaders() {
        assertThat(validator.isAllowed(null, null)).isFalse()
        assertThat(validator.isAllowed(" ", " ")).isFalse()
        assertThat(validator.isAllowed(null, "not a url")).isFalse()
        assertThat(validator.isAllowed(null, "/relative/path")).isFalse()
    }

    companion object {
        private const val ALLOWED_ORIGIN = "http://localhost:3000"
    }
}
