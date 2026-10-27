package com.merge.backend.domain.shift.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class UnavailableTimeRegisterReqBody(
    @field:NotNull(message = "시작 시간은 필수로 입력하여야 합니다.")
    val startAt: LocalDateTime?,

    @field:NotNull(message = "끝나는 시간은 필수로 입력하여야 합니다.")
    val endAt: LocalDateTime?,

    @field:NotNull
    val confirmOfficialShiftConflict: Boolean?
)
