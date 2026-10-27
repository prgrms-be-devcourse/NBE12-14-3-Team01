package com.merge.backend.domain.workplace.dto.response

import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import kotlin.jvm.JvmRecord

@JvmRecord
data class WorkplaceMemberResponse(
    val memberId: Long?,
    val name: String?,
    val email: String?,
    val role: WorkplaceRole?
) {
    companion object {
        @JvmStatic
        fun from(member: WorkplaceMember): WorkplaceMemberResponse =
            WorkplaceMemberResponse(
                member.id,
                member.user.name,
                member.user.email,
                member.role
            )
    }
}
