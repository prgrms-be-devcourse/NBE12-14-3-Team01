package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import java.time.LocalDateTime

@JvmRecord
data class ReceivedSubstituteRequestResponse(
    val candidateId: Long, // 내 Candidate ID → SUB-03에서 수락/거절할 때 사용
    val requestId: Long, // 대타 요청 ID
    val shiftId: Long, // 대타가 필요한 근무 ID
    val workplaceId: Long, // 어느 근무지인지
    val workplaceName: String, // 화면에 보여줄 근무지 이름
    val requesterMemberId: Long, // 대타를 요청한 직원의 Member ID
    val requesterName: String, // 대타 요청자 이름
    val startAt: LocalDateTime, // 대타 근무 시작 시간
    val endAt: LocalDateTime, // 대타 근무 종료 시간
    val requestStatus: RequestStatus, // 요청 상태 (여기서는 OPEN)
    val candidateStatus: CandidateStatus, // 내 응답 상태 (여기서는 PENDING)
    val createdAt: LocalDateTime, // 대타 요청이 생성된 시간
) {

    companion object {

        @JvmStatic
        fun from(
            candidate: SubstituteCandidate, // 조회된 Candidate 한 건
        ): ReceivedSubstituteRequestResponse {
            val request = candidate.request
            val shift = request.shift
            val workplace = shift.schedule.workplace

            return ReceivedSubstituteRequestResponse(
                candidateId = requireNotNull(candidate.id),
                requestId = requireNotNull(request.id),
                shiftId = requireNotNull(shift.id),
                workplaceId = requireNotNull(workplace.id),
                workplaceName = workplace.name,
                requesterMemberId = requireNotNull(request.requesterMember.id),
                requesterName = request.requesterMember.user.name,
                startAt = shift.startAt,
                endAt = shift.endAt,
                requestStatus = request.status,
                candidateStatus = candidate.status,
                createdAt = request.createDate,
            )
        }
    }
}
