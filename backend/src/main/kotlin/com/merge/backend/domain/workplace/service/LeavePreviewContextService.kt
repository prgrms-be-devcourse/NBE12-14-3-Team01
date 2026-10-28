package com.merge.backend.domain.workplace.service

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/** 읽은 원본을 서명한다. 로그인 Token이나 퇴사 확정 권한으로 사용하지 않는다. */
@Service
class LeavePreviewContextService(
    private val objectMapper: ObjectMapper,
    @Value("\${custom.jwt.secret-key}") secret: String
) {
    private val signingKey = SecretKeySpec(
        hmac(SecretKeySpec(secret.toByteArray(UTF_8), ALGORITHM), PURPOSE.toByteArray(UTF_8)),
        ALGORITHM
    )

    fun issue(
        actorUserId: Long,
        workplaceId: Long,
        memberId: Long,
        issuedAt: Instant,
        facts: LeavePreviewFacts
    ): String {
        val payload = Payload(
            schemaVersion = 1,
            actorUserId = actorUserId,
            workplaceId = workplaceId,
            memberId = memberId,
            issuedAt = issuedAt,
            expiresAt = issuedAt.plus(VALIDITY),
            facts = normalize(facts)
        )
        val content = "lv1." + encoder.encodeToString(objectMapper.writeValueAsBytes(payload))
        val signature = encoder.encodeToString(hmac(signingKey, content.toByteArray(UTF_8)))
        return "$content.$signature"
    }

    private fun normalize(facts: LeavePreviewFacts): LeavePreviewFacts = facts.copy(
        ongoingShifts = facts.ongoingShifts.sortedBy { it.shiftId },
        publishedShifts = facts.publishedShifts.sortedBy { it.shiftId },
        draftShiftsToDelete = facts.draftShiftsToDelete.sortedBy { it.shiftId },
        activeRequestsOnPublishedShifts = facts.activeRequestsOnPublishedShifts.sortedBy { it.requestId },
        candidacies = facts.candidacies.sortedWith(compareBy({ it.requestId }, { it.candidateId })),
        candidatesOnPublishedShifts = facts.candidatesOnPublishedShifts
            .sortedWith(compareBy({ it.requestId }, { it.candidateId }))
    )

    data class Payload(
        val schemaVersion: Int,
        val actorUserId: Long,
        val workplaceId: Long,
        val memberId: Long,
        val issuedAt: Instant,
        val expiresAt: Instant,
        val facts: LeavePreviewFacts
    )

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val PURPOSE = "switch:leave-preview:v1"
        private val VALIDITY = Duration.ofMinutes(15)
        private val encoder = Base64.getUrlEncoder().withoutPadding()

        private fun hmac(key: SecretKeySpec, bytes: ByteArray): ByteArray =
            Mac.getInstance(ALGORITHM).apply { init(key) }.doFinal(bytes)
    }
}
