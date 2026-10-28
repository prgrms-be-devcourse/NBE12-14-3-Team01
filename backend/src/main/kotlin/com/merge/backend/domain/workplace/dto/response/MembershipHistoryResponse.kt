package com.merge.backend.domain.workplace.dto.response

import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.service.MembershipHistory
import java.time.LocalDateTime

@JvmRecord
data class MembershipHistoryResponse(
    val memberId: Long,
    val workplaceName: String,
    val role: WorkplaceRole,
    val joinedAt: LocalDateTime,
    val leftAt: LocalDateTime
) {
    companion object {
        @JvmStatic
        fun from(history: MembershipHistory): MembershipHistoryResponse =
            MembershipHistoryResponse(
                memberId = history.memberId,
                workplaceName = history.workplaceName,
                role = history.role,
                joinedAt = history.joinedAt,
                leftAt = history.leftAt
            )
    }
}
