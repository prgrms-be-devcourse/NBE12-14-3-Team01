package com.merge.backend.domain.shift.dto

import com.merge.backend.domain.workplace.entity.WorkplaceRole
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class RegularShiftPatternListResponse(
    val patternId: Long?,
    val memberId: Long?,
    val memberName: String?,
    val role: WorkplaceRole?,
    val dayOfWeek: DayOfWeek?,
    val startTime: LocalTime?,
    val endTime: LocalTime?
)
