package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.workplace.entity.WorkplaceRole
import java.time.LocalDateTime

/** API 응답 형식과 분리된 본인의 종료 소속 조회 결과. */
data class MembershipHistory(
    val memberId: Long,
    val workplaceName: String,
    val role: WorkplaceRole,
    val joinedAt: LocalDateTime,
    val leftAt: LocalDateTime
)
