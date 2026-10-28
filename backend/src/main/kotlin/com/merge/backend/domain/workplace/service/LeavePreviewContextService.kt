package com.merge.backend.domain.workplace.service

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
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

    /**
     * 확정 Command용 원본 검증. 실패의 HTTP 매핑은 호출 Command의 책임이다.
     * 유효한 원본도 현재 권한·소속 또는 최신 DB 상태를 보장하지 않는다.
     * 발급 형식과 미리보기의 조회·응답 동작은 변경하지 않는다.
     */
    fun verify(
        context: String,
        actorUserId: Long,
        workplaceId: Long,
        memberId: Long,
        now: Instant
    ): Payload? {
        val parts = context.split('.')
        if (parts.size != 3 || parts[0] != "lv1") return null
        val payload = try {
            val signature = Base64.getUrlDecoder().decode(parts[2])
            val expected = hmac(signingKey, "${parts[0]}.${parts[1]}".toByteArray(UTF_8))
            if (!MessageDigest.isEqual(expected, signature)) return null
            objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Payload::class.java)
                ?: return null
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: tools.jackson.core.JacksonException) {
            return null
        }
        if (payload.schemaVersion != 1 || payload.actorUserId != actorUserId ||
            payload.workplaceId != workplaceId || payload.memberId != memberId ||
            payload.facts.member.memberId != memberId
        ) return null
        // 서명이 유효해도 비정상 발급 기간이나 미래 발급 시각을 허용하지 않는다.
        if (payload.issuedAt > now || !now.isBefore(payload.expiresAt) ||
            Duration.between(payload.issuedAt, payload.expiresAt) != VALIDITY
        ) return null
        return payload
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
