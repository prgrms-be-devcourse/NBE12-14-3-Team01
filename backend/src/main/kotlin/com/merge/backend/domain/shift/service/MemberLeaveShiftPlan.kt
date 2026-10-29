package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.substitute.service.ShiftChangeClosedRequest
import com.merge.backend.domain.workplace.entity.WorkplaceMember

/** 모든 Row가 전역 순서로 잠기고 V2의 원본 비교를 통과한 뒤 V3에 전달한다. */
data class MemberLeaveShiftPlan(
    val reassignments: List<Reassignment>,
    val cancellations: List<Shift>,
    val draftShiftsToDelete: List<Shift>
) {
    data class Reassignment(val shift: Shift, val target: WorkplaceMember)
}

data class MemberLeaveShiftResult(
    val closedRequests: List<ShiftChangeClosedRequest>,
    // 알림 계산용: 근무마다 그 근무에서 SHIFT_CHANGED로 종료된 요청 (NOTIFICATION-TYPES §3-5 근무 단위)
    val closedRequestsByShiftId: Map<Long, List<ShiftChangeClosedRequest>> = emptyMap(),
)
