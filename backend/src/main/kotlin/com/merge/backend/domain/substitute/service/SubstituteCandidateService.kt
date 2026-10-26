package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.event.SubstituteAllCandidatesRejectedEvent
import com.merge.backend.domain.substitute.event.SubstituteCandidateAcceptedEvent
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
                    member.user.id,
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
        val candidateUserId = candidate.member.user.id

        // 같은 User의 시간 상태를 변경하는 작업끼리 직렬화
        userService.getByIdForUpdate(candidateUserId)

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

        // 현재 Candidate를 제외하고 다른 PENDING 후보가 남아 있는지 확인
        val hasOtherPendingCandidate =
            substituteCandidateRepository.existsByRequest_IdAndStatusAndIdNot(
                request.id,
                CandidateStatus.PENDING,
                candidate.id,
            )

        // 다른 PENDING 후보가 없다면 마지막 후보가 거절한 것
        if (!hasOtherPendingCandidate) {
            request.closeAllCandidatesRejected(now)

            publishSubstituteAllCandidatesRejectedEvent(request)
        }

        return candidate
    }

    private fun publishSubstituteCandidateAcceptedEvent(candidate: SubstituteCandidate) {
        val request = candidate.request

        val workplaceId = request.shift.schedule.workplace.id

        val managerMemberIds = workplaceMemberRepository
            .findAllByWorkplace_IdAndRoleAndLeftAtIsNull(workplaceId, WorkplaceRole.MANAGER)
            .map { member -> member.id }

        applicationEventPublisher.publishEvent(
            SubstituteCandidateAcceptedEvent(
                requestId = request.id,
                managerMemberIds = managerMemberIds,
            ),
        )
    }

    private fun publishSubstituteAllCandidatesRejectedEvent(request: SubstituteRequest) {
        val workplaceId = request.shift.schedule.workplace.id

        val requesterMemberId = request.requesterMember.id

        val managerMemberIds = workplaceMemberRepository
            .findAllByWorkplace_IdAndRoleAndLeftAtIsNull(workplaceId, WorkplaceRole.MANAGER)
            .map { member -> member.id }

        val recipientMemberIds = (listOf(requesterMemberId) + managerMemberIds).distinct()

        applicationEventPublisher.publishEvent(
            SubstituteAllCandidatesRejectedEvent(
                requestId = request.id,
                recipientMemberIds = recipientMemberIds,
            ),
        )
    }
}
