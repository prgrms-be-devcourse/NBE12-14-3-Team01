package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import java.time.LocalDateTime

@JvmRecord
data class SubstituteRequestResponse(
    val requestId: Long,
    val shiftId: Long,
    val status: RequestStatus,
    val previousMemberId: Long,
    val newMemberId: Long,
    val newMemberName: String,
    val approvedAt: LocalDateTime?,
    val closedAt: LocalDateTime?,
) {

    companion object {

        @JvmStatic
        fun from(request: SubstituteRequest): SubstituteRequestResponse =
            SubstituteRequestResponse(
                requestId = requireNotNull(request.id),
                shiftId = requireNotNull(request.shift.id),
                status = RequestStatus.APPROVED,
                previousMemberId = requireNotNull(request.requesterMember.id),
                newMemberId = requireNotNull(request.shift.member.id),
                newMemberName = request.shift.member.user.name,
                approvedAt = request.approvedAt,
                closedAt = request.closedAt,
            )
    }
}
