package com.merge.backend.domain.shift.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class ShiftRequest(
    @field:NotNull
    val memberId: Long?,

    @field:NotNull
    val startAt: LocalDateTime?,

    @field:NotNull
    val endAt: LocalDateTime?,

    @field:NotNull
    val confirmUnavailableConflict: Boolean?
)
