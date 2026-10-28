package com.merge.backend.domain.shift.dto

import jakarta.validation.constraints.NotNull
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class RegularShiftPatternReqBody(
    @field:NotNull(message = "memberId는 필수로 입력해야 합니다.")
    val memberId: Long?,
    @field:NotNull(message = "요일은 필수로 입력해야 합니다.")
    val dayOfWeek: DayOfWeek?,
    @field:NotNull(message = "시작시간은 필수로 입력해야 합니다.")
    val startTime: LocalTime?,
    @field:NotNull(message = "끝나는 시간은 필수로 입력해야 합니다.")
    val endTime: LocalTime?
)
