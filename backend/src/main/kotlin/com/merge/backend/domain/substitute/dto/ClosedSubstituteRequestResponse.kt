package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import java.time.LocalDateTime

// API-SB-02 - 관리자: 근무지의 끝난 대타 요청 (종료·계산된 만료)
// 서버는 후속 조치를 판단하지 않고 사실 Field만 준다. User ID·소속 ID는 넣지 않는다 (API-CATALOG API-SB-02)
@JvmRecord
data class ClosedSubstituteRequestResponse(
    val requestId: Long,
    val shiftId: Long,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val shiftStatus: ShiftStatus,
    val outcome: Outcome,
    val closeReason: RequestCloseReason?,
    val closedAt: LocalDateTime, // 효력 종료 시각
    val requesterName: String,
    val requesterMembershipEnded: Boolean, // 요청 당시 소속 Row가 지금 종료됐는가
    val accepterName: String?, // ACCEPTED 후보가 있었던 경우만
    val requesterStillAssigned: Boolean, // 지금 근무 담당이 요청 당시 요청자 소속 Row인가
    val shiftStarted: Boolean,
    val hasNewerActiveRequest: Boolean, // 같은 근무에 더 최근의 진행 중 요청이 있는가
) {

    enum class Outcome {
        CLOSED,
        EXPIRED,
    }

    companion object {

        @JvmStatic
        fun from(
            request: SubstituteRequest,
            accepter: SubstituteCandidate?,
            hasNewerActiveRequest: Boolean,
            now: LocalDateTime,
        ): ClosedSubstituteRequestResponse {
            val shift = request.shift
            val requester = request.requesterMember

            // 목록에는 CLOSED와 계산된 만료만 온다. 만료는 SB-01·SUB-05처럼 사유 EXPIRED, 종료 시각은 근무 시작 시각 (G03-D-19)
            val isExpired = request.status != RequestStatus.CLOSED

            return ClosedSubstituteRequestResponse(
                requestId = requireNotNull(request.id),
                shiftId = requireNotNull(shift.id),
                startAt = shift.startAt,
                endAt = shift.endAt,
                shiftStatus = shift.status,
                outcome = if (isExpired) Outcome.EXPIRED else Outcome.CLOSED,
                closeReason = if (isExpired) RequestCloseReason.EXPIRED else request.closeReason,
                closedAt = if (isExpired) shift.startAt else requireNotNull(request.closedAt),
                requesterName = requester.user.name,
                requesterMembershipEnded = requester.leftAt != null,
                accepterName = accepter?.member?.user?.name,
                requesterStillAssigned = shift.member.id == requester.id,
                shiftStarted = shift.startAt <= now,
                hasNewerActiveRequest = hasNewerActiveRequest,
            )
        }
    }
}
