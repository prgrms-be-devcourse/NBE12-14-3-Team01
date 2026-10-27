package com.merge.backend.domain.substitute.event

@JvmRecord
data class SubstituteNoCandidateEvent(
    val shiftId: Long,
    val managerMemberIds: List<Long>,
)
