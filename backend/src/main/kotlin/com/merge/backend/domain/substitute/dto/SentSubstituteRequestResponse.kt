package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import java.time.LocalDateTime

@JvmRecord
data class SentSubstituteRequestResponse(
    val requestId: Long?,
    val shiftId: Long?,
    val workplaceId: Long?,
    val workplaceName: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val status: RequestStatus,
    val closeReason: RequestCloseReason?,
    val createdAt: LocalDateTime,
    val approvedAt: LocalDateTime?,
    val closedAt: LocalDateTime?,
) {

    companion object {

        private val ACTIVE_STATUSES = setOf(RequestStatus.OPEN, RequestStatus.ACCEPTED)

        @JvmStatic
        fun from(request: SubstituteRequest, now: LocalDateTime): SentSubstituteRequestResponse {
            val shift = request.shift
            val workplace = shift.schedule.workplace

            val response = SentSubstituteRequestResponse(
                requestId = request.id,
                shiftId = shift.id,
                workplaceId = workplace.id,
                workplaceName = workplace.name,
                startAt = shift.startAt,
                endAt = shift.endAt,
                status = request.status,
                closeReason = request.closeReason,
                createdAt = request.createDate,
                approvedAt = request.approvedAt,
                closedAt = request.closedAt,
            )

            val isExpired = request.status in ACTIVE_STATUSES && now >= shift.startAt

            return if (isExpired) {
                response.copy(
                    status = RequestStatus.CLOSED,
                    closeReason = RequestCloseReason.EXPIRED,
                    closedAt = now,
                )
            } else {
                response
            }
        }
    }
}
