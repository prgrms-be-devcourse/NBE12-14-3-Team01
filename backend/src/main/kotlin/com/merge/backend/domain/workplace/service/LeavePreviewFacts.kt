package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.ShiftStatus
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
    val candidacies: List<Candidacy>,
    // 담당 근무 요청 종료로 보존될 후보 이력·수신 자격도 비교 원본에 묶는다.
    val candidatesOnPublishedShifts: List<RequestCandidate> = emptyList()
) {
    data class Member(
        val memberId: Long,
        val name: String,
        val role: WorkplaceRole,
        val joinedAt: LocalDateTime
    )

    data class WorkShift(
        val shiftId: Long,
        val memberId: Long,
        val scheduleId: Long,
        val scheduleStatus: ScheduleStatus,
        val shiftStatus: ShiftStatus,
        val weekStartDate: LocalDate,
        val startAt: LocalDateTime,
        val endAt: LocalDateTime
    )

    data class ActiveRequest(
        val requestId: Long,
        val requesterMemberId: Long,
        val shiftId: Long,
        val status: RequestStatus,
        // ML-3-Q1: 해당 미래 공식 근무의 인계·취소 시 요청 종료 예상. 상태는 변경하지 않는다.
        val wouldCloseRequest: Boolean
    )

    data class Candidacy(
        val candidateId: Long,
        val memberId: Long,
        val requestId: Long,
        val requesterMemberId: Long,
        val shift: WorkShift,
        val requestStatus: RequestStatus,
        val candidateStatus: CandidateStatus,
        val otherCurrentPendingCandidates: Int,
        val wouldBecomeIneligible: Boolean,
        val wouldCloseRequest: Boolean
    )

    data class RequestCandidate(
        val candidateId: Long,
        val requestId: Long,
        val memberId: Long,
        val candidateStatus: CandidateStatus,
        val memberRole: WorkplaceRole,
        val memberLeftAt: LocalDateTime?
    )
}
