package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.substitute.dto.AcceptedClosedSubstituteRequestResponse
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.event.SubstituteAllCandidatesRejectedEvent
import com.merge.backend.domain.substitute.event.SubstituteCandidateAcceptedEvent
import com.merge.backend.domain.substitute.event.SubstituteCandidateIneligibleEvent
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.global.exception.BusinessException
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SubstituteCandidateService(
    private val workplaceMemberRepository: WorkplaceMemberRepository,
    private val shiftRepository: ShiftRepository,
    private val unavailableTimeRepository: UnavailableTimeRepository,
    private val substituteCandidateRepository: SubstituteCandidateRepository,
    private val substituteRequestRepository: SubstituteRequestRepository,
    private val userService: UserService,
    private val clock: Clock,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    @Transactional(readOnly = true)
    fun findCandidates(
        workplaceId: Long,
        requestMemberId: Long,
        targetShift: Shift,
    ): List<WorkplaceMember> {
        val now = LocalDateTime.now(clock)

        return workplaceMemberRepository.findAllByWorkplace_IdAndLeftAtIsNull(workplaceId)
            .asSequence()
            .filter { member -> member.role == WorkplaceRole.EMPLOYEE }
            .filter { member -> member.id != requestMemberId }
            .filterNot { member ->
                shiftRepository.existsOverlappingOfficialShift(
                    member.user.id,
                    ScheduleStatus.PUBLISHED,
                    targetShift.startAt,
                    targetShift.endAt,
                    targetShift.id,
                )
            }
            .filterNot { member ->
                unavailableTimeRepository.existsOverlappingUnavailableTime(
                    member.user.id,
                    targetShift.startAt,
                    targetShift.endAt,
                )
            }
            .filterNot { member ->
                substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                    requireNotNull(member.user.id),
                    targetShift.startAt,
                    targetShift.endAt,
                    now,
                )
            }
            .toList()
    }

    fun createCandidates(
        request: SubstituteRequest,
        members: List<WorkplaceMember>,
    ): List<SubstituteCandidate> {
        val candidates = members.map { member -> SubstituteCandidate(request, member) }

        return substituteCandidateRepository.saveAll(candidates)
    }

    @Transactional(readOnly = true)
    fun findReceivedRequests(
        userId: Long, // 현재 로그인한 User의 ID
    ): List<SubstituteCandidate> {
        val now = LocalDateTime.now(clock) // 현재 서버 시간

        return substituteCandidateRepository.findReceivedRequests(
            userId, // 누구에게 온 요청을 찾을지
            now, // 아직 시작하지 않은 Shift인지 판단할 기준 시간
        )
    }

    @Transactional(readOnly = true)
    fun findAcceptedPendingRequests(
        userId: Long, // 현재 로그인한 User의 ID
    ): List<SubstituteCandidate> {
        val now = LocalDateTime.now(clock) // 현재 서버 시간

        return substituteCandidateRepository.findAcceptedPendingRequests(
            userId, // 내가 수락한 Candidate인지 확인할 User ID
            now, // 아직 시작하지 않은 Shift인지 판단할 기준 시간
        )
    }

    @Transactional(readOnly = true)
    fun findAcceptedClosedRequests(
        userId: Long, // 현재 로그인한 User의 ID
    ): List<AcceptedClosedSubstituteRequestResponse> {
        val now = LocalDateTime.now(clock) // 현재 서버 시간

        // 효력 종료 시각 기준 최근 7일만 보여 줌 (API-CATALOG API-SB-01 조회 범위). DB Row는 그대로
        return substituteCandidateRepository.findAcceptedClosedRequests(userId, now, now.minusDays(7))
            .map { AcceptedClosedSubstituteRequestResponse.from(it, now) }
            .sortedWith(
                compareByDescending<AcceptedClosedSubstituteRequestResponse> { it.closedAt }
                    .thenByDescending { it.requestId },
            )
    }

    private fun validateRespondableCandidate(
        candidateId: Long, // 응답하려는 대타 후보 ID
        userId: Long, // 현재 로그인한 User ID
    ): SubstituteCandidate {
        val requestId = substituteCandidateRepository.findRequestIdById(candidateId)
            ?: throw BusinessException(SubstituteRequestErrorCode.CANDIDATE_NOT_FOUND)

        val request = substituteRequestRepository.findByIdForUpdate(requestId)
            ?: throw BusinessException(SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND)

        val candidate = substituteCandidateRepository.findByIdOrNull(candidateId)
            ?: throw BusinessException(SubstituteRequestErrorCode.CANDIDATE_NOT_FOUND)

        val member = candidate.member

        // 이 Candidate가 현재 로그인한 사용자의 것인지 확인
        if (member.user.id != userId) {
            throw BusinessException(SubstituteRequestErrorCode.NOT_OWN_CANDIDATE)
        }

        // 현재도 이 Workplace에 소속된 EMPLOYEE인지 확인
        if (member.leftAt != null || member.role != WorkplaceRole.EMPLOYEE) {
            throw BusinessException(SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER)
        }

        // 아직 응답하지 않은 Candidate인지 확인
        if (candidate.status != CandidateStatus.PENDING) {
            throw BusinessException(SubstituteRequestErrorCode.CANDIDATE_ALREADY_RESPONDED)
        }

        // 다른 Candidate가 먼저 수락했거나 이미 요청이 종료됐는지 확인
        if (request.status != RequestStatus.OPEN) {
            throw BusinessException(SubstituteRequestErrorCode.REQUEST_NOT_OPEN)
        }

        val shift = request.shift

        // 공개된 근무표의 Shift인지 확인
        if (shift.schedule.status != ScheduleStatus.PUBLISHED) {
            throw BusinessException(SubstituteRequestErrorCode.SHIFT_NOT_PUBLISHED)
        }

        // 취소되지 않은 정상 Shift인지 확인
        if (shift.status != ShiftStatus.SCHEDULED) {
            throw BusinessException(SubstituteRequestErrorCode.SHIFT_CANCELLED)
        }

        return candidate
    }

    private fun validateShiftNotStarted(shift: Shift, now: LocalDateTime) {
        if (shift.startAt <= now) {
            throw BusinessException(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED)
        }
    }

    @Transactional
    fun acceptCandidate(
        candidateId: Long, // 수락할 Candidate ID
        userId: Long, // 현재 로그인한 User ID
    ): SubstituteCandidate {
        // Request Lock을 획득하고
        // Candidate / Request의 기본 응답 가능 상태 확인
        val candidate = validateRespondableCandidate(candidateId, userId)

        val targetShift = candidate.request.shift
        val candidateUserId = requireNotNull(candidate.member.user.id)

        // 같은 User의 시간 상태를 변경하는 작업끼리 직렬화
        userService.getByIdForUpdate(candidateUserId)

        // 소속 Guard: 수락자 소속을 FOR SHARE로 잠가 퇴사와 직렬화한 뒤 DB 값으로 다시 확인 (ML-5 §5 대타 수락 행, ML5-T5)
        val memberId = requireNotNull(candidate.member.id)
        workplaceMemberRepository.findByIdForShare(memberId)
        if (
            workplaceMemberRepository.findCurrentEmployeeIdsIn(
                listOf(memberId),
                requireNotNull(targetShift.schedule.workplace.id),
            ).isEmpty()
        ) {
            throw BusinessException(SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER)
        }

        // Request/User Lock 대기 이후의 최신 시간
        val now = LocalDateTime.now(clock)

        validateShiftNotStarted(targetShift, now)

        // 1. 다른 공식 근무와 시간이 겹치는지 확인
        if (
            shiftRepository.existsOverlappingOfficialShift(
                candidateUserId,
                ScheduleStatus.PUBLISHED,
                targetShift.startAt,
                targetShift.endAt,
                targetShift.id,
            )
        ) {
            throw BusinessException(SubstituteRequestErrorCode.CONFLICT_SHIFT)
        }

        // 2. 근무 불가능 시간과 겹치는지 확인
        if (
            unavailableTimeRepository.existsOverlappingUnavailableTime(
                candidateUserId,
                targetShift.startAt,
                targetShift.endAt,
            )
        ) {
            throw BusinessException(SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME)
        }

        // 3. 이미 수락해둔 다른 대타 근무와 겹치는지 확인
        if (
            substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                candidateUserId,
                targetShift.startAt,
                targetShift.endAt,
                now,
            )
        ) {
            throw BusinessException(SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE)
        }

        // Candidate: PENDING → ACCEPTED
        // Request: OPEN → ACCEPTED
        candidate.acceptRequest(now)

        publishSubstituteCandidateAcceptedEvent(candidate)

        return candidate
    }

    @Transactional
    fun rejectCandidate(
        candidateId: Long, // 거절할 Candidate ID
        userId: Long, // 현재 로그인한 User ID
    ): SubstituteCandidate {
        // 수락/거절 공통 조건 확인
        val candidate = validateRespondableCandidate(candidateId, userId)

        val now = LocalDateTime.now(clock)

        validateShiftNotStarted(candidate.request.shift, now)

        val request = candidate.request

        // Candidate: PENDING → REJECTED
        candidate.reject(now)

        // 현재 Candidate를 제외하고 응답 가능한 후보가 남아 있는지 확인 (퇴사 처리와 같은 종료 판정, ML-3-P1)
        val hasOtherRespondableCandidate =
            substituteCandidateRepository.existsRespondableCandidateExcept(
                requireNotNull(request.id),
                requireNotNull(candidate.member.id),
            )

        // 응답 가능한 후보가 없다면 요청 종료
        // 나머지 후보가 전원 거절이면 ALL_CANDIDATES_REJECTED, 아니면 자격 상실(INELIGIBLE·퇴사·역할 변경)이 원인이라 CANDIDATE_INELIGIBLE
        if (!hasOtherRespondableCandidate) {
            val hasIneligibleCandidate = substituteCandidateRepository.existsByRequest_IdAndIdNotAndStatusNot(
                requireNotNull(request.id),
                requireNotNull(candidate.id),
                CandidateStatus.REJECTED,
            )

            if (hasIneligibleCandidate) {
                request.closeByCandidateIneligible(now)

                publishSubstituteCandidateIneligibleEvent(request)
            } else {
                request.closeAllCandidatesRejected(now)

                publishSubstituteAllCandidatesRejectedEvent(request)
            }
        }

        return candidate
    }

    // 퇴사 확정(V2)이 같은 Transaction 안에서 부름. 퇴사자 소속이 후보로 걸린 진행 중 요청을 정리한다 (ML-3-P·P1·A)
    // 알림은 내지 않고 결과만 돌려줌. 받는 사람은 퇴사 확정 결과로 V5가 정함 (#80 근무 변경 종료와 같은 방식)
    @Transactional(propagation = Propagation.MANDATORY)
    fun closeCandidaciesByMemberLeave(memberId: Long, now: LocalDateTime): MemberLeaveCandidacyResult {
        return closeMemberLeaveCandidacies(memberId, now,
            substituteRequestRepository.findActiveByCandidateMemberIdForUpdate(memberId, now))
    }

    /** V2 전용: 전체 Request 선점 후 호출한다. 기존 상태 전이를 재사용하고 새 Request는 잠그지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun closeCandidaciesByMemberLeave(
        memberId: Long, now: LocalDateTime, lockedRequestIds: Set<Long>
    ): MemberLeaveCandidacyResult {
        val requests = if (lockedRequestIds.isEmpty()) emptyList() else
            substituteRequestRepository.findActiveByCandidateMemberIdWithinIds(memberId, now, lockedRequestIds)
        return closeMemberLeaveCandidacies(memberId, now, requests)
    }

    private fun closeMemberLeaveCandidacies(
        memberId: Long, now: LocalDateTime, requests: List<SubstituteRequest>
    ): MemberLeaveCandidacyResult {
        val closedRequests = mutableListOf<CandidateIneligibleClosedRequest>()
        var ineligibleCandidacies = 0

        requests.forEach { request ->
            val requestId = requireNotNull(request.id)

            // Lock 전에 읽힌 Entity면 상태가 옛 값일 수 있어 DB 값으로 판단
            val requestStatus = substituteRequestRepository.findStatusById(requestId)
            val candidateStatus = substituteCandidateRepository.findStatusByRequestIdAndMemberId(requestId, memberId)

            when (candidateStatus) {
                // 수락자가 퇴사: 요청 종료, 수락 기록은 그대로 (ML-3-A)
                CandidateStatus.ACCEPTED -> {
                    request.closeByCandidateIneligible(now)
                    closedRequests += CandidateIneligibleClosedRequest(requestId, requireNotNull(request.requesterMember.id))
                }

                // 대기 후보가 퇴사: INELIGIBLE (ML-3-P). OPEN이고 응답 가능한 후보가 없으면 요청 종료 (ML-3-P1)
                CandidateStatus.PENDING -> {
                    requireNotNull(substituteCandidateRepository.findByRequestIdAndMemberId(requestId, memberId))
                        .markIneligible()
                    ineligibleCandidacies++

                    if (
                        requestStatus == RequestStatus.OPEN &&
                        !substituteCandidateRepository.existsRespondableCandidateExcept(requestId, memberId)
                    ) {
                        request.closeByCandidateIneligible(now)
                        closedRequests += CandidateIneligibleClosedRequest(requestId, requireNotNull(request.requesterMember.id))
                    }
                }

                else -> Unit
            }
        }

        return MemberLeaveCandidacyResult(closedRequests, ineligibleCandidacies)
    }

    private fun publishSubstituteCandidateAcceptedEvent(candidate: SubstituteCandidate) {
        val request = candidate.request

        val workplaceId = request.shift.schedule.workplace.id

        val managerMemberIds = workplaceMemberRepository
            .findAllByWorkplace_IdAndRoleAndLeftAtIsNull(workplaceId, WorkplaceRole.MANAGER)
            .map { member -> requireNotNull(member.id) }

        applicationEventPublisher.publishEvent(
            SubstituteCandidateAcceptedEvent(
                requestId = requireNotNull(request.id),
                managerMemberIds = managerMemberIds,
            ),
        )
    }

    private fun publishSubstituteAllCandidatesRejectedEvent(request: SubstituteRequest) {
        applicationEventPublisher.publishEvent(
            SubstituteAllCandidatesRejectedEvent(
                requestId = requireNotNull(request.id),
                recipientMemberIds = requesterAndManagerMemberIds(request),
            ),
        )
    }

    // 받는 사람은 거절 종료와 같은 요청자 + 현재 관리자 (NOTIFICATION-TYPES SUBSTITUTE_CANDIDATE_INELIGIBLE)
    private fun publishSubstituteCandidateIneligibleEvent(request: SubstituteRequest) {
        applicationEventPublisher.publishEvent(
            SubstituteCandidateIneligibleEvent(
                requestId = requireNotNull(request.id),
                recipientMemberIds = requesterAndManagerMemberIds(request),
            ),
        )
    }

    private fun requesterAndManagerMemberIds(request: SubstituteRequest): List<Long> {
        val workplaceId = request.shift.schedule.workplace.id

        val requesterMemberId = requireNotNull(request.requesterMember.id)

        val managerMemberIds = workplaceMemberRepository
            .findAllByWorkplace_IdAndRoleAndLeftAtIsNull(workplaceId, WorkplaceRole.MANAGER)
            .map { member -> requireNotNull(member.id) }

        return (listOf(requesterMemberId) + managerMemberIds).distinct()
    }
}
