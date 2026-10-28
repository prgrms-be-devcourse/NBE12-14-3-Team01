package com.merge.backend.domain.shift.dto

import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftAssignmentHistory
import com.merge.backend.domain.shift.entity.ShiftAssignmentReason
import com.merge.backend.domain.shift.entity.ShiftCancellationReason
import java.time.LocalDateTime

// API-SC-01 - 빠진 근무 (공개 후 취소되거나 다른 직원에게 넘어간 내 근무)
// 새 담당자의 이름·ID는 넣지 않는다 (API-CATALOG API-SC-01, FRONTEND-CONTRACT §9-2)
@JvmRecord
data class RemovedShiftResponse(
    val shiftId: Long,
    val workplaceId: Long,
    val workplaceName: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val event: RemovedShiftEvent,
    val occurredAt: LocalDateTime, // 사건 시각 (취소 시각 / 담당자 변경 시각)
    val reason: RemovedShiftReason,
) {

    companion object {

        @JvmStatic
        fun fromCancelled(shift: Shift): RemovedShiftResponse {
            val workplace = shift.schedule.workplace

            return RemovedShiftResponse(
                shiftId = requireNotNull(shift.id),
                workplaceId = requireNotNull(workplace.id),
                workplaceName = workplace.name,
                startAt = shift.startAt,
                endAt = shift.endAt,
                event = RemovedShiftEvent.CANCELLED,
                occurredAt = requireNotNull(shift.cancelledAt),
                reason = when (requireNotNull(shift.cancellationReason)) {
                    ShiftCancellationReason.MEMBER_LEFT -> RemovedShiftReason.MEMBER_LEFT
                    ShiftCancellationReason.MANAGER_CANCELLED -> RemovedShiftReason.MANAGER_CANCELLED
                },
            )
        }

        @JvmStatic
        fun fromReassigned(history: ShiftAssignmentHistory): RemovedShiftResponse {
            val shift = history.shift
            val workplace = shift.schedule.workplace

            return RemovedShiftResponse(
                shiftId = requireNotNull(shift.id),
                workplaceId = requireNotNull(workplace.id),
                workplaceName = workplace.name,
                startAt = shift.startAt,
                endAt = shift.endAt,
                event = RemovedShiftEvent.REASSIGNED,
                occurredAt = history.createDate,
                reason = when (history.reason) {
                    ShiftAssignmentReason.MEMBER_LEFT -> RemovedShiftReason.MEMBER_LEFT
                    ShiftAssignmentReason.MANAGER_REASSIGNED -> RemovedShiftReason.MANAGER_REASSIGNED
                },
            )
        }
    }
}

enum class RemovedShiftEvent {
    CANCELLED,
    REASSIGNED,
}

enum class RemovedShiftReason {
    MEMBER_LEFT,
    MANAGER_CANCELLED,
    MANAGER_REASSIGNED,
}
