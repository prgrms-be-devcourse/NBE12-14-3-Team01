package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.RequestStatus

@JvmRecord
data class SubstituteRequestCreateResponse(
    val requestCreated: Boolean,
    val requestId: Long?,
    val shiftId: Long?,
    val status: RequestStatus?,
    val candidateCount: Int,
)
