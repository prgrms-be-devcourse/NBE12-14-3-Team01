package com.merge.backend.domain.shift.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class PublishedShiftCreateRequest(
    @field:NotNull
    val memberId: Long?,
    @field:NotNull
    val startAt: LocalDateTime?,
    @field:NotNull
    val endAt: LocalDateTime?,
    @field:NotNull
    @field:Valid
    val acknowledgedUnavailableConflicts: List<AcknowledgedNewShiftUnavailableConflict>?,
)

@JvmRecord
data class AcknowledgedNewShiftUnavailableConflict(
    @field:NotNull
    @field:Valid
    val newShiftKey: NewShiftKey?,
    @field:NotNull
    @field:Valid
    val overlaps: List<UnavailableOverlap>?,
)

@JvmRecord
data class NewShiftKey(
    @field:NotNull val scheduleId: Long?,
    @field:NotNull val targetMemberId: Long?,
    @field:NotNull val shiftStartAt: LocalDateTime?,
    @field:NotNull val shiftEndAt: LocalDateTime?,
)
