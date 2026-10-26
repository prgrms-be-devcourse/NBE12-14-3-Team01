package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import java.time.LocalDateTime

@JvmRecord
data class SubstituteCandidateRespondResponse(
    val candidateId: Long?, // 응답한 Candidate ID
    val requestId: Long?, // 연결된 대타 요청 ID
    val candidateStatus: CandidateStatus, // ACCEPTED 또는 REJECTED
    val requestStatus: RequestStatus, // 처리 후 Request 상태
    val respondedAt: LocalDateTime?, // 수락/거절한 시간
    val closeReason: RequestCloseReason?, // 마지막 거절로 CLOSED된 경우 종료 사유
) {

    companion object {

        @JvmStatic
        fun from(candidate: SubstituteCandidate): SubstituteCandidateRespondResponse =
            SubstituteCandidateRespondResponse(
                candidateId = candidate.id,
                requestId = candidate.request.id,
                candidateStatus = candidate.status,
                requestStatus = candidate.request.status,
                respondedAt = candidate.respondedAt,
                closeReason = candidate.request.closeReason,
            )
    }
}
