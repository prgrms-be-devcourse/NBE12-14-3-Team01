package com.merge.backend.domain.shift.dto

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class SchedulePublishResponse(
    val scheduleId: Long?,
    val workplaceId: Long?,
    val weekStartDate: LocalDate?,
    val status: ScheduleStatus?,
    val publishedAt: LocalDateTime?
) {
    companion object {
        @JvmStatic
        fun from(schedule: Schedule): SchedulePublishResponse =
            SchedulePublishResponse(
                schedule.id,
                schedule.workplace.id,
                schedule.weekStartDate,
                schedule.status,
                schedule.publishedAt
            )
    }
}
