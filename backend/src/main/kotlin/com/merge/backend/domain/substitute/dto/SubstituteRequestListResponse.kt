package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import java.time.LocalDateTime

@JvmRecord
data class SubstituteRequestListResponse(
    val requestId: Long,
    val shiftId: Long,
    val requesterMemberId: Long,
    val requesterName: String,
    val workplaceId: Long,
    val workplaceName: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val status: RequestStatus,
    val createdAt: LocalDateTime,
    val acceptedMember: AcceptedMemberResponse?,
) {

    // acceptedMember 응답 전용 규격 DTO
    @JvmRecord
    data class AcceptedMemberResponse(
        val memberId: Long,
        val name: String,
    ) {

        companion object {

            fun from(candidate: SubstituteCandidate): AcceptedMemberResponse =
                AcceptedMemberResponse(
                    memberId = candidate.member.persistedId,
                    name = candidate.member.user.name,
                )
        }
    }

    companion object {

        @JvmStatic
        fun from(
            request: SubstituteRequest,
            acceptedCandidateMap: Map<Long, SubstituteCandidate>,
        ): SubstituteRequestListResponse {
            // 수락자 정보 추출
            val acceptedMember =
                if (request.status == RequestStatus.ACCEPTED) {
                    acceptedCandidateMap[request.id]?.let(AcceptedMemberResponse::from)
                } else {
                    null
                }

            val shift = request.shift
            val workplace = shift.schedule.workplace

            return SubstituteRequestListResponse(
                requestId = request.persistedId,
                shiftId = shift.persistedId,
                requesterMemberId = request.requesterMember.persistedId,
                requesterName = request.requesterMember.user.name,
                workplaceId = workplace.persistedId,
                workplaceName = workplace.name,
                startAt = shift.startAt,
                endAt = shift.endAt,
                status = request.status,
                createdAt = request.createDate,
                acceptedMember = acceptedMember,
            )
        }
    }
}
