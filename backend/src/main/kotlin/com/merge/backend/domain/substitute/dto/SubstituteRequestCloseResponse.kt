package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import java.time.LocalDateTime

@JvmRecord
data class SubstituteRequestCloseResponse(
    val requestId: Long,
    val shiftId: Long,
    val status: RequestStatus,
    val closeReason: RequestCloseReason?,
    val closedAt: LocalDateTime?,
) {

    companion object {

        @JvmStatic
        fun from(request: SubstituteRequest): SubstituteRequestCloseResponse =
            SubstituteRequestCloseResponse(
                requestId = requireNotNull(request.id),
                shiftId = requireNotNull(request.shift.id),
                status = request.status,
                closeReason = request.closeReason,
                closedAt = request.closedAt,
            )
    }
}
