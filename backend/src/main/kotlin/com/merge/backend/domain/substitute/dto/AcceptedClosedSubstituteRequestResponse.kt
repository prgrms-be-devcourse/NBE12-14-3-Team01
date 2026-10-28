package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import java.time.LocalDateTime

// API-SB-01 - 내가 수락했던 대타 요청 중 끝난 것
// 현재 근무 담당자와 다른 후보 정보는 넣지 않는다 (API-CATALOG API-SB-01)
@JvmRecord
data class AcceptedClosedSubstituteRequestResponse(
    val requestId: Long,
    val workplaceId: Long,
    val workplaceName: String,
    val requesterName: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val requestStatus: RequestStatus,
    val closeReason: RequestCloseReason?,
    val candidateStatus: CandidateStatus,
    val closedAt: LocalDateTime, // 효력 종료 시각
) {

    companion object {

        private val ACTIVE_STATUSES = setOf(RequestStatus.OPEN, RequestStatus.ACCEPTED)

        @JvmStatic
        fun from(candidate: SubstituteCandidate, now: LocalDateTime): AcceptedClosedSubstituteRequestResponse {
            val request = candidate.request
            val shift = request.shift
            val workplace = shift.schedule.workplace

            // 승인 전에 근무가 시작된 요청은 SUB-05처럼 CLOSED/EXPIRED, 종료 시각은 근무 시작 시각 (G03-D-19)
            val isExpired = request.status in ACTIVE_STATUSES && now >= shift.startAt

            return AcceptedClosedSubstituteRequestResponse(
                requestId = requireNotNull(request.id),
                workplaceId = requireNotNull(workplace.id),
                workplaceName = workplace.name,
                requesterName = request.requesterMember.user.name,
                startAt = shift.startAt,
                endAt = shift.endAt,
                requestStatus = if (isExpired) RequestStatus.CLOSED else request.status,
                closeReason = if (isExpired) RequestCloseReason.EXPIRED else request.closeReason,
                candidateStatus = candidate.status,
                closedAt = if (isExpired) shift.startAt else requireNotNull(request.closedAt),
            )
        }
    }
}
