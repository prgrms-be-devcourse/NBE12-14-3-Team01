package com.merge.backend.domain.workplace.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

/** 성공한 전체 Transaction의 실제 결과만 전달한다. 현재 POST에 연결되지 않은 DTO다. */
data class LeaveConfirmResponse(
    val leftAt: LocalDateTime,
    val reassignedShifts: List<LeavePreviewResponse.WorkShift>,
    val cancelledShifts: List<LeavePreviewResponse.WorkShift>,
    val deletedDraftShifts: List<LeavePreviewResponse.WorkShift>,
    val closedRequests: ClosedRequests,
    val ineligibleCandidacies: Int,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val newInviteCode: String?
) {
    /** 근무 수나 후보 수가 아니라 이번 Transaction에서 종료한 서로 다른 Request를 센다. */
    data class ClosedRequests private constructor(val total: Int, val byReason: Map<String, Int>) {
        companion object {
            fun from(shiftChangedRequestIds: Set<Long>, candidateIneligibleRequestIds: Set<Long>): ClosedRequests {
                require(shiftChangedRequestIds.intersect(candidateIneligibleRequestIds).isEmpty()) {
                    "한 대타 요청을 두 종료 사유로 집계할 수 없습니다."
                }
                return ClosedRequests(
                    shiftChangedRequestIds.size + candidateIneligibleRequestIds.size,
                    linkedMapOf(
                        "SHIFT_CHANGED" to shiftChangedRequestIds.size,
                        "CANDIDATE_INELIGIBLE" to candidateIneligibleRequestIds.size
                    )
                )
            }
        }
    }
}
