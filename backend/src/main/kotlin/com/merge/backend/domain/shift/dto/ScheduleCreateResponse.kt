package com.merge.backend.domain.shift.dto

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import java.time.LocalDate
import kotlin.jvm.JvmRecord

@JvmRecord
data class ScheduleCreateResponse(
    val scheduleId: Long?,
    val workplaceId: Long?,
    val weekStartDate: LocalDate?,
    val status: ScheduleStatus?,
    val shiftCount: Int
) {
    companion object {
        @JvmStatic
        fun from(
            schedule: Schedule,
            shiftCount: Int
        ): ScheduleCreateResponse =
            ScheduleCreateResponse(
                schedule.id,
                schedule.workplace.id,
                schedule.weekStartDate,
                schedule.status,
                shiftCount
            )
    }
}
