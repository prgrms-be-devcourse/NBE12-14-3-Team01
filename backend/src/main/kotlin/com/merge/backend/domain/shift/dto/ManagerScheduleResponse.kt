package com.merge.backend.domain.shift.dto

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmSuppressWildcards

@JvmRecord
data class ManagerScheduleResponse(
    val scheduleId: Long?,
    val workplaceId: Long?,
    val weekStartDate: LocalDate?,
    val status: ScheduleStatus?,
    val publishedAt: LocalDateTime?,
    val shifts: List<ScheduleShiftResponse>?
) {
    companion object {
        @JvmStatic
        fun from(
            schedule: Schedule,
            shifts: List<@JvmSuppressWildcards Shift>
        ): ManagerScheduleResponse {
            val shiftResponses =
                shifts.stream()
                    .map(ScheduleShiftResponse::from)
                    .toList()

            return ManagerScheduleResponse(
                schedule.id,
                schedule.workplace.id,
                schedule.weekStartDate,
                schedule.status,
                schedule.publishedAt,
                shiftResponses
            )
        }
    }
}
