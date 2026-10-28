package com.merge.backend.domain.shift.event

import com.merge.backend.domain.substitute.service.ShiftChangeClosedRequest

// 공개 후 근무 변경 (API-PS-01 추가, API-PS-02 취소, API-PS-03 재배정).
// 업무 Transaction 안에서 발행하고, 받는 사람·문구는 알림 쪽이 같은 Transaction에서 정한다 (NOTIFICATION-TYPES §3-2 R1)
data class PublishedShiftChangedEvent(
    val kind: PublishedShiftChangeKind,
    val shiftId: Long?,
    val previousMemberId: Long?, // 취소·재배정 전 담당 (추가는 null)
    val newMemberId: Long?, // 추가·재배정의 새 담당 (취소는 null)
    val actorMemberId: Long?, // 실행한 관리자 소속
    val closedRequests: List<ShiftChangeClosedRequest>, // 이 변경으로 SHIFT_CHANGED 종료된 대타 요청
)

enum class PublishedShiftChangeKind {
    ADDED,
    CANCELLED,
    REASSIGNED,
}
