package com.merge.backend.domain.workplace.dto.response

import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import kotlin.jvm.JvmRecord

@JvmRecord
data class WorkplaceCreateResponse(
    val workplaceId: Long?,
    val name: String?,
    val role: WorkplaceRole?
) {
    companion object {
        @JvmStatic
        fun from(member: WorkplaceMember): WorkplaceCreateResponse =
            WorkplaceCreateResponse(
                member.workplace.id,
                member.workplace.name,
                member.role
            )
    }
}