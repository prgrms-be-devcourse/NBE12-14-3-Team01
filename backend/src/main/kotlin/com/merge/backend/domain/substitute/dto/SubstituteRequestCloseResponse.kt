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
                requestId = request.persistedId,
                shiftId = request.shift.persistedId,
                status = request.status,
                closeReason = request.closeReason,
                closedAt = request.closedAt,
            )
    }
}
