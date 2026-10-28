package com.merge.backend.domain.shift.dto

import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class ShiftReassignResponse(
    val shift: ReassignedShiftResponse,
    val closedSubstituteRequests: Int,
) {
    companion object {
        @JvmStatic
        fun from(shift: Shift, closedSubstituteRequests: Int) = ShiftReassignResponse(
            ReassignedShiftResponse.from(shift), closedSubstituteRequests,
        )
    }
}

@JvmRecord
data class ReassignedShiftResponse(
    val shiftId: Long?,
    val memberId: Long?,
    val memberName: String?,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
    val status: ShiftStatus?,
) {
    companion object {
        @JvmStatic
        fun from(shift: Shift) = ReassignedShiftResponse(
            shift.id, shift.member.id, shift.member.user.name, shift.startAt, shift.endAt, shift.status,
        )
    }
}
