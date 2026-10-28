package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import java.time.LocalDateTime

@JvmRecord
data class SentSubstituteRequestResponse(
    val requestId: Long,
    val shiftId: Long,
    val workplaceId: Long,
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
                requestId = requireNotNull(request.id),
                shiftId = requireNotNull(shift.id),
                workplaceId = requireNotNull(workplace.id),
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

            // 만료는 DB에 저장하지 않고 응답에서만 계산. 종료 시각은 조회 시각이 아니라 요청이 효력을 잃은 근무 시작 시각 (G03-D-19)
            return if (isExpired) {
                response.copy(
                    status = RequestStatus.CLOSED,
                    closeReason = RequestCloseReason.EXPIRED,
                    closedAt = shift.startAt,
                )
            } else {
                response
            }
        }
    }
}
