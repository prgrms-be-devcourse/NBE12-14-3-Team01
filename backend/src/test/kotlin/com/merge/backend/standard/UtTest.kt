package com.merge.backend.standard

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UtTest {

    private val secretKey = "test-secret-key-for-jwt-unit-test-1234567890"
    private val expireMillis = 1000L * 60 * 10

    @Test
    fun JWT를_생성하고_payload를_그대로_복원한다() {
        val payload: Map<String, Any> = mapOf("id" to 1, "email" to "test@example.com")

        val jwt = Ut.jwt.toString(secretKey, expireMillis, payload)

        assertThat(jwt).isNotBlank()
        assertThat(Ut.jwt.isValid(jwt, secretKey)).isTrue()
        assertThat(Ut.jwt.payloadOrNull(jwt, secretKey)).containsAllEntriesOf(payload)
    }

    @Test
    fun 다른_시크릿키로_검증하면_유효하지_않다() {
        val jwt = Ut.jwt.toString(secretKey, expireMillis, mapOf("id" to 1))

        assertThat(Ut.jwt.isValid(jwt, "different-secret-key-1234567890abcdef")).isFalse()
    }

    @Test
    fun 만료된_토큰은_유효하지_않다() {
        val jwt = Ut.jwt.toString(secretKey, -1000L, mapOf("id" to 1))

        assertThat(Ut.jwt.isValid(jwt, secretKey)).isFalse()
    }
}
