package com.merge.backend.domain.substitute.event

@JvmRecord
data class SubstituteRequestCreatedEvent(
    val requestId: Long,
    val candidateMemberIds: List<Long>,
)
