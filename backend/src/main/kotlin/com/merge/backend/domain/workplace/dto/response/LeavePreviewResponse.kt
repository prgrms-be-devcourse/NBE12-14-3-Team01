package com.merge.backend.domain.workplace.dto.response

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.service.LeavePreviewFacts
import java.time.LocalDate
import java.time.LocalDateTime

data class LeavePreviewResponse(
    val member: Member,
    val canLeave: Boolean,
    val blockingReason: String?,
    val ongoingShift: WorkShift?,
    val publishedShifts: List<PublishedShift>,
    val draftShiftsToDelete: List<WorkShift>,
    val candidacies: List<Candidacy>,
    val previewContext: String
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
    ) {
        companion object {
            fun from(shift: LeavePreviewFacts.WorkShift): WorkShift = WorkShift(
                shift.shiftId, shift.memberId, shift.scheduleId, shift.scheduleStatus,
                shift.shiftStatus, shift.weekStartDate, shift.startAt, shift.endAt
            )
        }
    }

    data class PublishedShift(
        val shiftId: Long,
        val memberId: Long,
        val scheduleId: Long,
        val scheduleStatus: ScheduleStatus,
        val shiftStatus: ShiftStatus,
        val startAt: LocalDateTime,
        val endAt: LocalDateTime,
        val activeRequests: List<ActiveRequest>
    )

    data class ActiveRequest(
        val requestId: Long,
        val requesterMemberId: Long,
        val status: RequestStatus,
        val wouldCloseRequest: Boolean
    )

    data class Candidacy(
        val candidateId: Long,
        val requestId: Long,
        val requesterMemberId: Long,
        val shift: WorkShift,
        val requestStatus: RequestStatus,
        val candidateStatus: CandidateStatus,
        val otherCurrentPendingCandidates: Int,
        val wouldBecomeIneligible: Boolean,
        val wouldCloseRequest: Boolean
    )

    companion object {
        fun from(facts: LeavePreviewFacts, previewContext: String): LeavePreviewResponse {
            val requestsByShift = facts.activeRequestsOnPublishedShifts.groupBy { it.shiftId }
            // 비정상 중복 근무가 있어도 모든 진행 중 근무가 끝나기 전 확정 가능으로 안내하지 않는다.
            val ongoing = facts.ongoingShifts.maxWithOrNull(compareBy({ it.endAt }, { it.shiftId }))
            return LeavePreviewResponse(
                member = Member(facts.member.memberId, facts.member.name, facts.member.role, facts.member.joinedAt),
                canLeave = ongoing == null,
                blockingReason = if (ongoing == null) null else "ONGOING_SHIFT",
                ongoingShift = ongoing?.let(WorkShift::from),
                publishedShifts = facts.publishedShifts.map { shift ->
                    PublishedShift(
                        shift.shiftId, shift.memberId, shift.scheduleId, shift.scheduleStatus,
                        shift.shiftStatus, shift.startAt, shift.endAt,
                        requestsByShift[shift.shiftId].orEmpty().map {
                            ActiveRequest(it.requestId, it.requesterMemberId, it.status, it.wouldCloseRequest)
                        }
                    )
                },
                draftShiftsToDelete = facts.draftShiftsToDelete.map(WorkShift::from),
                candidacies = facts.candidacies.map {
                    Candidacy(
                        it.candidateId, it.requestId, it.requesterMemberId, WorkShift.from(it.shift),
                        it.requestStatus, it.candidateStatus, it.otherCurrentPendingCandidates,
                        it.wouldBecomeIneligible, it.wouldCloseRequest
                    )
                },
                previewContext = previewContext
            )
        }
    }
}
