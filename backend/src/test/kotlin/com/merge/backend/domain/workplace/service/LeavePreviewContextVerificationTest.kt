package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.workplace.entity.WorkplaceRole
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class LeavePreviewContextVerificationTest {
    private val mapper = jacksonObjectMapper()
    private val secret = "test-only-leave-preview-secret"
    private val service = LeavePreviewContextService(mapper, secret)
    private val issuedAt = Instant.parse("2026-10-28T03:00:00Z")
    private val facts = LeavePreviewFacts(
        LeavePreviewFacts.Member(20, "직원", WorkplaceRole.EMPLOYEE, LocalDateTime.of(2026, 10, 1, 9, 0)),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
    )

    @Test
    fun acceptsIssuedContextUntilButNotAtExpiry() {
        val token = service.issue(1, 10, 20, issuedAt, facts)
        assertThat(verify(token, issuedAt)?.facts).isEqualTo(facts)
        assertThat(verify(token, issuedAt.plusSeconds(899))).isNotNull()
        assertThat(verify(token, issuedAt.plusSeconds(900))).isNull()
        assertThat(verify(token, issuedAt.plusSeconds(901))).isNull()
        assertThat(verify(token, issuedAt.minusNanos(1))).isNull()
    }

    @Test
    fun rejectsDifferentActorWorkplaceOrMembership() {
        val token = service.issue(1, 10, 20, issuedAt, facts)
        assertThat(service.verify(token, 2, 10, 20, issuedAt)).isNull()
        assertThat(service.verify(token, 1, 11, 20, issuedAt)).isNull()
        assertThat(service.verify(token, 1, 10, 21, issuedAt)).isNull()
        assertThat(verify(service.issue(1, 10, 20, issuedAt,
            facts.copy(member = facts.member.copy(memberId = 21))))).isNull()
    }

    @Test
    fun rejectsTamperingDifferentKeyAndMalformedInput() {
        val token = service.issue(1, 10, 20, issuedAt, facts)
        val parts = token.split('.')
        val changedPayload = encoder.encodeToString(mapper.writeValueAsBytes(
            payload().copy(actorUserId = 2)
        ))
        assertThat(service.verify("lv1.$changedPayload.${parts[2]}", 2, 10, 20, issuedAt)).isNull()
        assertThat(LeavePreviewContextService(mapper, "another-test-secret")
            .verify(token, 1, 10, 20, issuedAt)).isNull()
        for (invalid in listOf("", "lv1", "lv1..", "$token.extra", "lv2.${parts[1]}.${parts[2]}",
            "lv1.${parts[1]}.!", sign("not json"), sign("{}"), sign("null"))) {
            assertThat(verify(invalid)).isNull()
        }
    }

    @Test
    fun rejectsSignedUnsupportedVersionAndInvalidValidityPeriod() {
        for (invalid in listOf(
            payload().copy(schemaVersion = 2),
            payload().copy(expiresAt = issuedAt.plusSeconds(901)),
            payload().copy(expiresAt = issuedAt),
            payload().copy(expiresAt = issuedAt.minusSeconds(1))
        )) {
            assertThat(verify(sign(mapper.writeValueAsString(invalid)))).isNull()
        }
    }

    @Test
    fun acceptsContextIssuedByAnotherInstanceWithSameKey() {
        val otherInstance = LeavePreviewContextService(jacksonObjectMapper(), secret)
        assertThat(verify(otherInstance.issue(1, 10, 20, issuedAt, facts))?.facts).isEqualTo(facts)
    }

    private fun verify(token: String, now: Instant = issuedAt) = service.verify(token, 1, 10, 20, now)

    private fun payload() = LeavePreviewContextService.Payload(1, 1, 10, 20,
        issuedAt, issuedAt.plusSeconds(900), facts)

    private val encoder = Base64.getUrlEncoder().withoutPadding()

    private fun sign(json: String): String {
        fun hmac(key: ByteArray, bytes: ByteArray) = Mac.getInstance("HmacSHA256")
            .apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(bytes)
        val key = hmac(secret.toByteArray(UTF_8), "switch:leave-preview:v1".toByteArray(UTF_8))
        val content = "lv1." + encoder.encodeToString(json.toByteArray(UTF_8))
        return "$content.${encoder.encodeToString(hmac(key, content.toByteArray(UTF_8)))}"
    }
}
