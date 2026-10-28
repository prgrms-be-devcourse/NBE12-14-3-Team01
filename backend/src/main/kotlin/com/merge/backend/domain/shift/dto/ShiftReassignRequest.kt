package com.merge.backend.domain.shift.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class ShiftReassignRequest(
    @field:NotNull
    val targetMemberId: Long?,
    @field:NotNull
    @field:Valid
    val acknowledgedUnavailableConflicts: List<AcknowledgedUnavailableConflict>?,
)

@JvmRecord
data class AcknowledgedUnavailableConflict(
    @field:NotNull
    val shiftId: Long?,
    @field:NotNull
    val targetMemberId: Long?,
    @field:NotNull
    @field:Valid
    val overlaps: List<UnavailableOverlap>?,
)

@JvmRecord
data class UnavailableOverlap(
    @field:NotNull val startAt: LocalDateTime?,
    @field:NotNull val endAt: LocalDateTime?,
)
