package com.merge.backend.domain.substitute.event

@JvmRecord
data class SubstituteRequestApprovedEvent(
    val requestId: Long,
    val requesterMemberId: Long,
    val acceptedMemberId: Long,
)
