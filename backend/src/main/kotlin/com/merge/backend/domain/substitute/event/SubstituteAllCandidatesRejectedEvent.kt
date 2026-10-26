package com.merge.backend.domain.substitute.event

@JvmRecord
data class SubstituteAllCandidatesRejectedEvent(
    val requestId: Long,
    val recipientMemberIds: List<Long>,
)
