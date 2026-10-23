package com.merge.backend.domain.substitute.event

@JvmRecord
data class SubstituteRequestManagerClosedEvent(
    val requestId: Long,
    val recipientMemberIds: List<Long>,
)
