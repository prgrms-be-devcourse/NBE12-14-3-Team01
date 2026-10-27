package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import java.time.LocalDateTime

@JvmRecord
data class AcceptedPendingSubstituteRequestResponse(
    val candidateId: Long,
    val requestId: Long,
    val shiftId: Long,
    val workplaceId: Long,
    val workplaceName: String,
    val requesterName: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val requestStatus: RequestStatus,
    val candidateStatus: CandidateStatus,
    val respondedAt: LocalDateTime?,
) {

    companion object {

        @JvmStatic
        fun from(
            candidate: SubstituteCandidate, // 조회된 Candidate 한 건
        ): AcceptedPendingSubstituteRequestResponse {
            val request = candidate.request
            val shift = request.shift
            val workplace = shift.schedule.workplace

            return AcceptedPendingSubstituteRequestResponse(
                candidateId = requireNotNull(candidate.id),
                requestId = requireNotNull(request.id),
                shiftId = requireNotNull(shift.id),
                workplaceId = requireNotNull(workplace.id),
                workplaceName = workplace.name,
                requesterName = request.requesterMember.user.name,
                startAt = shift.startAt,
                endAt = shift.endAt,
                requestStatus = request.status,
                candidateStatus = candidate.status,
                respondedAt = candidate.respondedAt,
            )
        }
    }
}
