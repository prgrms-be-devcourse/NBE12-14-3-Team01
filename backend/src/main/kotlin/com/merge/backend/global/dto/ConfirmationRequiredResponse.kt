package com.merge.backend.global.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
@JvmRecord
data class ConfirmationRequiredResponse(
    val requiresConfirmation: Boolean,
    val unavailableConflicts: List<UnavailableConflictResponse>? = null,
)

@JvmRecord
data class UnavailableConflictResponse(
    val shiftId: Long?,
    val shiftStartAt: LocalDateTime?,
    val shiftEndAt: LocalDateTime?,
    val targetMemberId: Long?,
    val targetName: String?,
    val overlaps: List<UnavailableConflictOverlap>,
)

@JvmRecord
data class UnavailableConflictOverlap(
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
)
