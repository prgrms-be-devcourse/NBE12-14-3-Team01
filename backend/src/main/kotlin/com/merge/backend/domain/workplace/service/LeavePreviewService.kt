package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.workplace.repository.LeavePreviewQueryRepository
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeavePreviewService(
    private val clock: Clock,
    private val workplaceMemberService: WorkplaceMemberService,
    private val leavePreviewQueryRepository: LeavePreviewQueryRepository,
    private val substituteRequestRepository: SubstituteRequestRepository,
    private val substituteCandidateRepository: SubstituteCandidateRepository
) {
    /** Lock·상태 변경·확정용 Token 발행 없이 조회 시점의 사실을 읽는다. */
    @Transactional(readOnly = true)
    fun getPreviewFacts(actorUserId: Long, workplaceId: Long, memberId: Long): LeavePreviewFacts {
        val member = workplaceMemberService.requireLeaveTarget(actorUserId, workplaceId, memberId)
        val now = LocalDateTime.now(clock)
        val shifts = leavePreviewQueryRepository.findAffectedShifts(workplaceId, memberId, now)
        val publishedShifts = shifts.filter { it.schedule.status == ScheduleStatus.PUBLISHED }
        val futureShifts = publishedShifts.filter { it.startAt > now }
        val futureShiftIds = futureShifts.map { checkNotNull(it.id) }.toSet()

        // 기존 V4 조회가 미래 PUBLISHED·SCHEDULED 근무의 OPEN·ACCEPTED 요청을 제한한다.
        val activeRequests = substituteRequestRepository.findAllByWorkplaceId(workplaceId, now)
        val otherRequests = activeRequests.filter { it.shift.member.id != memberId }
        val otherRequestIds = otherRequests.map { checkNotNull(it.id) }
        val pendingCandidates = if (otherRequestIds.isEmpty()) {
            emptyList()
        } else {
            substituteCandidateRepository.findByRequestIdInAndStatus(
                otherRequestIds, CandidateStatus.PENDING
            )
        }
        val acceptedCandidates = if (otherRequestIds.isEmpty()) {
            emptyList()
        } else {
            substituteCandidateRepository.findByRequestIdInAndStatus(
                otherRequestIds, CandidateStatus.ACCEPTED
            )
        }
        val requestsById = otherRequests.associateBy { checkNotNull(it.id) }
        val otherPendingCounts = pendingCandidates
            .filter { it.member.id != memberId && it.member.isCurrent() }
            .groupingBy { checkNotNull(it.request.id) }
            .eachCount()

        return LeavePreviewFacts(
            member = LeavePreviewFacts.Member(
                memberId = checkNotNull(member.id),
                name = member.user.name,
                role = member.role,
                joinedAt = member.joinedAt
            ),
            ongoingShifts = publishedShifts.filter { it.startAt <= now }.map(::toWorkShift),
            publishedShifts = futureShifts.map(::toWorkShift),
            draftShiftsToDelete = shifts.filter { it.schedule.status == ScheduleStatus.DRAFT }
                .map(::toWorkShift),
            activeRequestsOnPublishedShifts = activeRequests
                .filter { it.shift.id in futureShiftIds }
                .map {
                    LeavePreviewFacts.ActiveRequest(
                        requestId = checkNotNull(it.id),
                        shiftId = checkNotNull(it.shift.id),
                        status = it.status
                    )
                },
            candidacies = (pendingCandidates + acceptedCandidates)
                .filter { it.member.id == memberId && it.member.workplace.id == workplaceId }
                .map {
                    val requestId = checkNotNull(it.request.id)
                    val requestStatus = checkNotNull(requestsById[requestId]).status
                    val otherPendingCount = otherPendingCounts[requestId] ?: 0
                    // ML-3-P/P1/A: 자격 상실과 요청 종료의 예상 효과만 계산한다.
                    // ACCEPTED 후보의 수락 이력은 퇴사로 변경하지 않는다.
                    val wouldCloseRequest =
                        (it.status == CandidateStatus.ACCEPTED && requestStatus == RequestStatus.ACCEPTED) ||
                            (it.status == CandidateStatus.PENDING && requestStatus == RequestStatus.OPEN &&
                                otherPendingCount == 0)
                    LeavePreviewFacts.Candidacy(
                        candidateId = checkNotNull(it.id),
                        requestId = requestId,
                        requestStatus = requestStatus,
                        candidateStatus = it.status,
                        otherCurrentPendingCandidates = otherPendingCount,
                        wouldBecomeIneligible = it.status == CandidateStatus.PENDING,
                        wouldCloseRequest = wouldCloseRequest
                    )
                }
                .sortedWith(compareBy({ it.requestId }, { it.candidateId }))
        )
    }

    private fun toWorkShift(shift: Shift): LeavePreviewFacts.WorkShift =
        LeavePreviewFacts.WorkShift(
            shiftId = checkNotNull(shift.id),
            scheduleId = checkNotNull(shift.schedule.id),
            weekStartDate = shift.schedule.weekStartDate,
            startAt = shift.startAt,
            endAt = shift.endAt
        )
}
