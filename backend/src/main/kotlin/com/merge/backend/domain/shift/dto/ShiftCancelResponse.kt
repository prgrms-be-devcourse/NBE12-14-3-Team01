package com.merge.backend.domain.shift.dto

import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftCancellationReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class ShiftCancelResponse(
    val shift: CancelledShiftResponse,
    val closedSubstituteRequests: Int,
) {
    companion object {
        @JvmStatic
        fun from(
            shift: Shift,
            closedSubstituteRequests: Int,
        ): ShiftCancelResponse =
            ShiftCancelResponse(
                CancelledShiftResponse.from(shift),
                closedSubstituteRequests,
            )
    }
}

@JvmRecord
data class CancelledShiftResponse(
    val shiftId: Long?,
    val memberId: Long?,
    val memberName: String?,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
    val status: ShiftStatus?,
    val cancellationReason: ShiftCancellationReason?,
    val cancelledAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun from(shift: Shift): CancelledShiftResponse =
            CancelledShiftResponse(
                shift.id,
                shift.member.id,
                shift.member.user.name,
                shift.startAt,
                shift.endAt,
                shift.status,
                shift.cancellationReason,
                shift.cancelledAt,
            )
    }
}
