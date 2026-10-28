package com.merge.backend.domain.workplace.dto.response

@JvmRecord
data class MembershipHistoryListResponse(
    val memberships: List<MembershipHistoryResponse>
)
