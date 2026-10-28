package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.substitute.entity.CandidateStatus
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class LeavePreviewContextServiceTest {
    private val mapper = jacksonObjectMapper()
    private val secret = "test-only-leave-preview-secret"
    private val service = LeavePreviewContextService(mapper, secret)
    private val issuedAt = Instant.parse("2026-10-28T03:00:00Z")
    private val facts = LeavePreviewFacts(
        LeavePreviewFacts.Member(20, "직원", WorkplaceRole.EMPLOYEE, LocalDateTime.of(2026, 10, 1, 9, 0)),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
    )

    @Test
    fun bindIdentityOriginalFactsAndFifteenMinuteValidityToPurposeDerivedSignature() {
        val token = service.issue(1, 10, 20, issuedAt, facts)
        val parts = token.split('.')
        assertThat(parts).hasSize(3)
        assertThat(parts[0]).isEqualTo("lv1")
        val payload = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), LeavePreviewContextService.Payload::class.java)
        assertThat(payload.schemaVersion).isEqualTo(1)
        assertThat(payload.actorUserId).isEqualTo(1)
        assertThat(payload.workplaceId).isEqualTo(10)
        assertThat(payload.memberId).isEqualTo(20)
        assertThat(payload.issuedAt).isEqualTo(issuedAt)
        assertThat(payload.expiresAt).isEqualTo(issuedAt.plusSeconds(900))
        assertThat(payload.facts).isEqualTo(facts)

        val key = hmac(secret.toByteArray(UTF_8), "switch:leave-preview:v1".toByteArray(UTF_8))
        val expected = hmac(key, "${parts[0]}.${parts[1]}".toByteArray(UTF_8))
        assertThat(Base64.getUrlDecoder().decode(parts[2])).isEqualTo(expected)
        assertThat(hmac(secret.toByteArray(UTF_8), "${parts[0]}.${parts[1]}".toByteArray(UTF_8)))
            .isNotEqualTo(expected)
        assertThat(service.issue(2, 10, 20, issuedAt, facts)).isNotEqualTo(token)
        assertThat(service.issue(1, 11, 20, issuedAt, facts)).isNotEqualTo(token)
        assertThat(service.issue(1, 10, 21, issuedAt, facts)).isNotEqualTo(token)
    }

    private fun hmac(key: ByteArray, content: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(content)

    @Test
    fun normalizeCandidateOrderAndBindCandidateHistoryAndCurrentMembership() {
        val first = LeavePreviewFacts.RequestCandidate(1, 30, 40, CandidateStatus.PENDING, WorkplaceRole.EMPLOYEE, null)
        val second = first.copy(candidateId = 2, memberId = 41)
        val original = facts.copy(candidatesOnPublishedShifts = listOf(first, second))
        val token = service.issue(1, 10, 20, issuedAt, original)

        assertThat(service.issue(1, 10, 20, issuedAt,
            original.copy(candidatesOnPublishedShifts = listOf(second, first))))
            .isEqualTo(token)
        assertThat(service.issue(1, 10, 20, issuedAt,
            original.copy(candidatesOnPublishedShifts = listOf(first.copy(candidateStatus = CandidateStatus.ACCEPTED), second))))
            .isNotEqualTo(token)
        assertThat(service.issue(1, 10, 20, issuedAt,
            original.copy(candidatesOnPublishedShifts = listOf(first.copy(memberLeftAt = facts.member.joinedAt), second))))
            .isNotEqualTo(token)
    }
}
