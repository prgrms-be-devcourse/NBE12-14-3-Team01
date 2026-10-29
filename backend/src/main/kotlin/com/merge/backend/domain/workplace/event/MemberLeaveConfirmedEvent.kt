package com.merge.backend.domain.workplace.event

import com.merge.backend.domain.substitute.service.CandidateIneligibleClosedRequest
import com.merge.backend.domain.substitute.service.ShiftChangeClosedRequest

// 퇴사 확정(API-LV-02) Commit 대상. 소속 종료(leftAt 기록) 뒤 같은 Transaction에서 발행해 퇴사자는 받는 사람에서 빠진다 (R1).
// 받는 사람·문구는 알림 쪽이 정한다 (NOTIFICATION-TYPES §3-1·§3-3 "퇴사 확정", §6-3-2 S3-A, V5-N-20)
data class MemberLeaveConfirmedEvent(
    val workplaceId: Long,
    val leaverMemberId: Long,
    val actorMemberId: Long,
    val reassignments: List<Reassignment>, // 근무마다 인계받은 직원
    val cancelledShiftIds: List<Long>,
    val shiftChangedRequestsByShiftId: Map<Long, List<ShiftChangeClosedRequest>>, // 근무 변경으로 SHIFT_CHANGED 종료
    val candidateIneligibleRequests: List<CandidateIneligibleClosedRequest>, // 퇴사자가 후보였던 요청의 CANDIDATE_INELIGIBLE 종료
) {
    data class Reassignment(val shiftId: Long, val targetMemberId: Long)
}
