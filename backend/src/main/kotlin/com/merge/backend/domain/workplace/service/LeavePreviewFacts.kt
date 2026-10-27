package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * API DTO나 확정 계획과 분리하여 조회 시점의 사실만 담는 내부 모델.
 * 후보의 예상 영향은 ML-3 정책으로 계산하며 실제 상태를 변경하지 않는다.
 * previewContext나 미구현 상태 전이를 대신하지 않는다.
 */
data class LeavePreviewFacts(
    val member: Member,
    val ongoingShifts: List<WorkShift>,
    val publishedShifts: List<WorkShift>,
    val draftShiftsToDelete: List<WorkShift>,
    val activeRequestsOnPublishedShifts: List<ActiveRequest>,
    val candidacies: List<Candidacy>
) {
    data class Member(
        val memberId: Long,
        val name: String,
        val role: WorkplaceRole,
        val joinedAt: LocalDateTime
    )

    data class WorkShift(
        val shiftId: Long,
        val scheduleId: Long,
        val weekStartDate: LocalDate,
        val startAt: LocalDateTime,
        val endAt: LocalDateTime
    )

    data class ActiveRequest(
        val requestId: Long,
        val shiftId: Long,
        val status: RequestStatus
    )

    data class Candidacy(
        val candidateId: Long,
        val requestId: Long,
        val requestStatus: RequestStatus,
        val candidateStatus: CandidateStatus,
        val otherCurrentPendingCandidates: Int,
        val wouldBecomeIneligible: Boolean,
        val wouldCloseRequest: Boolean
    )
}
