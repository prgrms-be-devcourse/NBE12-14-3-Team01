package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import java.time.LocalDateTime

@JvmRecord
data class SubstituteRequestResponse(
    val requestId: Long?,
    val shiftId: Long?,
    val status: RequestStatus,
    val previousMemberId: Long?,
    val newMemberId: Long?,
    val newMemberName: String,
    val approvedAt: LocalDateTime?,
    val closedAt: LocalDateTime?,
) {

    companion object {

        @JvmStatic
        fun from(request: SubstituteRequest): SubstituteRequestResponse =
            SubstituteRequestResponse(
                requestId = request.id,
                shiftId = request.shift.id,
                status = RequestStatus.APPROVED,
                previousMemberId = request.requesterMember.id,
                newMemberId = request.shift.member.id,
                newMemberName = request.shift.member.user.name,
                approvedAt = request.approvedAt,
                closedAt = request.closedAt,
            )
    }
}
