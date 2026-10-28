package com.merge.backend.domain.substitute.service

// 근무 변경으로 종료한 대타 요청. 호출한 쪽이 근무 변경 알림과 합쳐 받는 사람을 정할 때 씀
data class ShiftChangeClosedRequest(
    val requestId: Long,
    val requesterMemberId: Long,
    // 요청이 ACCEPTED였을 때 수락한 후보, 아니면 null
    val acceptedMemberId: Long?,
)
