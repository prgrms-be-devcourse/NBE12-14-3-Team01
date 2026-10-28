package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.workplace.dto.response.LeavePreviewResponse
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.LeavePreviewQueryRepository
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class LeavePreviewService(
    private val clock: Clock,
    private val workplaceMemberService: WorkplaceMemberService,
    private val leavePreviewQueryRepository: LeavePreviewQueryRepository,
    private val previewContextService: LeavePreviewContextService
) {
    /** 같은 DB 스냅샷에서 읽은 표시값과 원본을 연결한다. 명시적 Lock·업무 쓰기는 없다. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun getPreview(actorUserId: Long, workplaceId: Long, memberId: Long): LeavePreviewResponse {
        val issuedAt = clock.instant()
        val facts = readFacts(actorUserId, workplaceId, memberId, LocalDateTime.ofInstant(issuedAt, clock.zone))
        val context = previewContextService.issue(actorUserId, workplaceId, memberId, issuedAt, facts)
        return LeavePreviewResponse.from(facts, context)
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun getPreviewFacts(actorUserId: Long, workplaceId: Long, memberId: Long): LeavePreviewFacts =
        readFacts(actorUserId, workplaceId, memberId, LocalDateTime.now(clock))

    private fun readFacts(
        actorUserId: Long,
        workplaceId: Long,
        memberId: Long,
        now: LocalDateTime
    ): LeavePreviewFacts {
        val member = workplaceMemberService.requireLeaveTarget(actorUserId, workplaceId, memberId)
        val shifts = leavePreviewQueryRepository.findAffectedShifts(workplaceId, memberId, now)
        val publishedShifts = shifts.filter { it.schedule.status == ScheduleStatus.PUBLISHED }
        val futureShifts = publishedShifts.filter { it.startAt > now }
        val futureShiftIds = futureShifts.map { checkNotNull(it.id) }.toSet()

        // V4의 활성 조건은 유지하고, 대상이 영향을 주는 요청으로 조회 범위를 제한한다.
        val activeRequests = leavePreviewQueryRepository.findAffectedRequests(workplaceId, memberId, now)
        val otherRequests = activeRequests.filter { it.shift.member.id != memberId }
        val requestIds = activeRequests.map { checkNotNull(it.id) }
        val candidates = if (requestIds.isEmpty()) {
            emptyList()
        } else {
            leavePreviewQueryRepository.findAffectedCandidates(requestIds)
        }
        val requestsById = otherRequests.associateBy { checkNotNull(it.id) }
        val otherPendingCounts = candidates
            .filter {
                it.status == CandidateStatus.PENDING &&
                    it.member.id != memberId && it.member.leftAt == null &&
                    it.member.workplace.id == workplaceId &&
                    it.member.role == WorkplaceRole.EMPLOYEE
            }
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
                        requesterMemberId = checkNotNull(it.requesterMember.id),
                        shiftId = checkNotNull(it.shift.id),
                        status = it.status,
                        wouldCloseRequest = true
                    )
                },
            candidacies = candidates
                .filter {
                    it.member.id == memberId && it.member.workplace.id == workplaceId &&
                        it.request.id in requestsById
                }
                .map {
                    val requestId = checkNotNull(it.request.id)
                    val request = checkNotNull(requestsById[requestId])
                    val requestStatus = request.status
                    val otherPendingCount = otherPendingCounts[requestId] ?: 0
                    // ML-3-P/P1/A: 자격 상실과 요청 종료의 예상 효과만 계산한다.
                    // ACCEPTED 후보의 수락 이력은 퇴사로 변경하지 않는다.
                    val wouldCloseRequest =
                        (it.status == CandidateStatus.ACCEPTED && requestStatus == RequestStatus.ACCEPTED) ||
                            (it.status == CandidateStatus.PENDING && requestStatus == RequestStatus.OPEN &&
                                otherPendingCount == 0)
                    LeavePreviewFacts.Candidacy(
                        candidateId = checkNotNull(it.id),
                        memberId = checkNotNull(it.member.id),
                        requestId = requestId,
                        requesterMemberId = checkNotNull(request.requesterMember.id),
                        shift = toWorkShift(request.shift),
                        requestStatus = requestStatus,
                        candidateStatus = it.status,
                        otherCurrentPendingCandidates = otherPendingCount,
                        wouldBecomeIneligible = it.status == CandidateStatus.PENDING,
                        wouldCloseRequest = wouldCloseRequest
                    )
                }
                .sortedWith(compareBy({ it.requestId }, { it.candidateId })),
            candidatesOnPublishedShifts = candidates
                .filter { it.request.shift.id in futureShiftIds }
                .map {
                    LeavePreviewFacts.RequestCandidate(
                        checkNotNull(it.id), checkNotNull(it.request.id), checkNotNull(it.member.id),
                        it.status, it.member.role, it.member.leftAt
                    )
                }
        )
    }

    private fun toWorkShift(shift: Shift): LeavePreviewFacts.WorkShift =
        LeavePreviewFacts.WorkShift(
            shiftId = checkNotNull(shift.id),
            memberId = checkNotNull(shift.member.id),
            scheduleId = checkNotNull(shift.schedule.id),
            scheduleStatus = shift.schedule.status,
            shiftStatus = shift.status,
            weekStartDate = shift.schedule.weekStartDate,
            startAt = shift.startAt,
            endAt = shift.endAt
        )
}
