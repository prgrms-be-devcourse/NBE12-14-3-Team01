package com.merge.backend.domain.workplace.dto.response

import com.merge.backend.domain.workplace.entity.Workplace
import kotlin.jvm.JvmRecord

@JvmRecord
data class WorkplaceInviteCodeResponse(
    val workplaceId: Long?,
    val inviteCode: String?
) {
    companion object {
        @JvmStatic
        fun from(workplace: Workplace): WorkplaceInviteCodeResponse =
            WorkplaceInviteCodeResponse(
                workplace.id,
                workplace.inviteCode
            )
    }
}
