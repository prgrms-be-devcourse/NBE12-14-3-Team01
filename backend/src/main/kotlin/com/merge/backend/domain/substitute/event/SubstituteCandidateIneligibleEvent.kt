package com.merge.backend.domain.substitute.event

// 응답 가능한 후보가 없어져 CANDIDATE_INELIGIBLE로 종료 (거절 경로). 알림 Type은 V5 (NOTIFICATION-TYPES SUBSTITUTE_CANDIDATE_INELIGIBLE)
@JvmRecord
data class SubstituteCandidateIneligibleEvent(
    val requestId: Long,
    val recipientMemberIds: List<Long>,
)
