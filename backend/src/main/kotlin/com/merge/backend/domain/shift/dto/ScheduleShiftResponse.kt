package com.merge.backend.domain.shift.dto

import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class ScheduleShiftResponse(
    val shiftId: Long?,
    val memberId: Long?,
    val memberName: String?,
    val role: WorkplaceRole?,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
    val status: ShiftStatus?
) {
    companion object {
        @JvmStatic
        fun from(shift: Shift): ScheduleShiftResponse =
            ScheduleShiftResponse(
                shift.id,
                shift.member.id,
                shift.member.user.name,
                shift.member.role,
                shift.startAt,
                shift.endAt,
                shift.status
            )
    }
}
