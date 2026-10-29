package com.merge.backend.domain.workplace.event

// 가입·재가입 성공 (POST /workplaces/join). 관리자 알림 WORKPLACE_MEMBER_JOINED (ML-H2 H2-10·H2-11)
data class WorkplaceMemberJoinedEvent(
    val workplaceId: Long?,
    val memberId: Long?,
)
