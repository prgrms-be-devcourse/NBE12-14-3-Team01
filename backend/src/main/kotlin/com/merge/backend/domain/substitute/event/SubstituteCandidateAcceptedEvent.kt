package com.merge.backend.domain.substitute.event

@JvmRecord
data class SubstituteCandidateAcceptedEvent(
    val requestId: Long,
    val managerMemberIds: List<Long>,
)
