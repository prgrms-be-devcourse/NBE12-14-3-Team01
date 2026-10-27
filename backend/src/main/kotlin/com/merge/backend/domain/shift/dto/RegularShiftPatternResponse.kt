package com.merge.backend.domain.shift.dto

import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class RegularShiftPatternResponse(
    val patternId: Long?,
    val memberId: Long?,
    val memberName: String?,
    val dayOfWeek: DayOfWeek?,
    val startTime: LocalTime?,
    val endTime: LocalTime?
)
