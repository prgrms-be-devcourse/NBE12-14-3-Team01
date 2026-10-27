package com.merge.backend.domain.workplace.dto.response

import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import kotlin.jvm.JvmRecord

@JvmRecord
data class MyWorkplaceResponse(
    val workplaceId: Long?,
    val name: String,
    val role: WorkplaceRole
) {
    companion object {
        @JvmStatic
        fun from(member: WorkplaceMember): MyWorkplaceResponse =
            MyWorkplaceResponse(
                member.workplace.id,
                member.workplace.name,
                member.role
            )
    }
}