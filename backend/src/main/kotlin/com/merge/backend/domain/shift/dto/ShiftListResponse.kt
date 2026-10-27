package com.merge.backend.domain.shift.dto

import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmSuppressWildcards

@JvmRecord
data class ShiftListResponse(
    val weekStartDate: LocalDate?,
    val shifts: List<ShiftItem>?
) {
    companion object {
        @JvmStatic
        fun of(
            weekStartDate: LocalDate?,
            shifts: List<@JvmSuppressWildcards Shift>?
        ): ShiftListResponse {
            val shiftItems = requireNotNull(shifts)
                .stream()
                .map(ShiftItem::from)
                .toList()

            return ShiftListResponse(weekStartDate, shiftItems)
        }
    }

    @JvmRecord
    data class ShiftItem(
        val shiftId: Long?,
        val scheduleId: Long?,
        val workplaceId: Long?,
        val workplaceName: String?,
        val startAt: LocalDateTime?,
        val endAt: LocalDateTime?,
        val status: ShiftStatus?
    ) {
        companion object {
            @JvmStatic
            fun from(shift: Shift): ShiftItem =
                ShiftItem(
                    shift.id,
                    shift.schedule.id,
                    shift.member.workplace.id,
                    shift.member.workplace.name,
                    shift.startAt,
                    shift.endAt,
                    shift.status
                )
        }
    }
}
