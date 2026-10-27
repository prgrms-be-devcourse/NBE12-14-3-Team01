package com.merge.backend.domain.shift.dto

import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class ShiftDetailResponse(
    val shiftId: Long?,
    val scheduleId: Long?,
    val workplaceId: Long?,
    val workplaceName: String?,
    val weekStartDate: LocalDate?,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
    val status: ShiftStatus?,
    val publishedAt: LocalDateTime?
) {
    companion object {
        @JvmStatic
        fun from(shift: Shift): ShiftDetailResponse =
            ShiftDetailResponse(
                shift.id,
                shift.schedule.id,
                shift.schedule.workplace.id,
                shift.schedule.workplace.name,
                shift.schedule.weekStartDate,
                shift.startAt,
                shift.endAt,
                shift.status,
                shift.schedule.publishedAt
            )
    }
}
