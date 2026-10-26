package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.exception.ScheduleErrorCode
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.substitute.dto.SentSubstituteRequestResponse
import com.merge.backend.domain.substitute.dto.SubstituteRequestCreateResponse
import com.merge.backend.domain.substitute.dto.SubstituteRequestListResponse
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.event.SubstituteNoCandidateEvent
import com.merge.backend.domain.substitute.event.SubstituteRequestApprovedEvent
import com.merge.backend.domain.substitute.event.SubstituteRequestCreatedEvent
import com.merge.backend.domain.substitute.event.SubstituteRequestManagerClosedEvent
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.domain.workplace.service.WorkplaceMemberService
import com.merge.backend.global.exception.BusinessException
import java.time.Clock
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubstituteRequestService(
    private val substituteRequestRepository: SubstituteRequestRepository,
    private val substituteCandidateService: SubstituteCandidateService,
    private val substituteCandidateRepository: SubstituteCandidateRepository,
    private val shiftRepository: ShiftRepository,
    private val workplaceRepository: WorkplaceRepository,
    private val unavailableTimeRepository: UnavailableTimeRepository,
    private val userService: UserService,
    private val workplaceMemberService: WorkplaceMemberService,
    private val workplaceMemberRepository: WorkplaceMemberRepository,
    private val clock: Clock,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun create(shiftId: Long?, actorUserId: Long?): SubstituteRequestCreateResponse {
        val shift = validateSubstituteRequest(shiftId, actorUserId)

        // 조건에 맞는 대타 후보들을 먼저 찾음
        val candidates = substituteCandidateService.findCandidates(
            shift.schedule.workplace.id, // 대상 근무지 ID
            shift.member.id, // 대타를 요청한 멤버 ID
            shift, // 대타가 필요한 Shift
        )

        // 후보가 한 명도 없으면 Request 자체를 만들지 않음
        if (candidates.isEmpty()) {
            publishSubstituteNoCandidateEvent(shift)

            return SubstituteRequestCreateResponse(
                requestCreated = false, // Request 생성 안 됨
                requestId = null, // 생성된 Request가 없으므로 null
                shiftId = shiftId, // 요청 대상 Shift ID
                status = null, // Request가 없으므로 null
                candidateCount = 0, // 후보 0명
            )
        }

        val request = substituteRequestRepository.save(
            SubstituteRequest(shift, shift.member, RequestStatus.OPEN),
        )

        // 찾은 후보들로 SubstituteCandidate 생성
        val createdCandidates = substituteCandidateService.createCandidates(
            request, // 방금 생성한 대타 요청
            candidates, // 앞에서 찾은 대타 후보 목록
        )

        publishSubstituteRequestCreatedEvent(request, createdCandidates)

        return SubstituteRequestCreateResponse(
            requestCreated = true, // Request 생성 성공
            requestId = request.id, // 생성된 Request ID
            shiftId = shiftId, // 대상 Shift ID
            status = request.status, // OPEN
            candidateCount = createdCandidates.size, // 실제 생성된 Candidate 수
        )
    }

    @Transactional(readOnly = true)
    fun getSentRequests(actorUserId: Long?): List<SentSubstituteRequestResponse> {
        val now = LocalDateTime.now(clock)

        val requests =
            substituteRequestRepository.findAllByRequesterMember_User_IdOrderByCreateDateDesc(
                actorUserId,
            )

        return requests.map { request -> SentSubstituteRequestResponse.from(request, now) }
    }

    private fun validateSubstituteRequest(shiftId: Long?, actorUserId: Long?): Shift {
        val shift = shiftRepository.findByIdForUpdate(shiftId)
            .orElseThrow { BusinessException(SubstituteRequestErrorCode.SHIFT_NOT_FOUND) }

        if (shift.schedule.status != ScheduleStatus.PUBLISHED) {
            throw BusinessException(SubstituteRequestErrorCode.SHIFT_NOT_PUBLISHED)
        }

        if (shift.status != ShiftStatus.SCHEDULED) {
            throw BusinessException(SubstituteRequestErrorCode.SHIFT_CANCELLED)
        }

        if (shift.member.user.id != actorUserId) {
            throw BusinessException(SubstituteRequestErrorCode.NOT_OWN_SHIFT)
        }

        if (shift.startAt <= LocalDateTime.now(clock)) {
            throw BusinessException(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED)
        }

        if (
            substituteRequestRepository.existsByShift_IdAndStatusIn(
                shiftId,
                listOf(RequestStatus.OPEN, RequestStatus.ACCEPTED),
            )
        ) {
            throw BusinessException(SubstituteRequestErrorCode.ACTIVE_REQUEST_EXISTS)
        }

        return shift
    }

    @Transactional(readOnly = true)
    fun list(workplaceId: Long?, actorId: Long?): List<SubstituteRequestListResponse> {
        workplaceRepository.findById(requireNotNull(workplaceId))
            .orElseThrow { BusinessException(WorkplaceErrorCode.WORKPLACE_NOT_FOUND) }

        workplaceMemberService.requireManager(actorId, workplaceId)

        // requests 조회
        val requests = substituteRequestRepository
            .findAllByWorkplaceId(workplaceId, LocalDateTime.now(clock))

        // ACCEPTED 상태들 선별
        val acceptedRequestIds = requests
            .filter { request -> request.status == RequestStatus.ACCEPTED }
            .map { request -> request.id }

        val acceptedCandidateMap = getAcceptedCandidates(acceptedRequestIds)

        return requests.map { request ->
            SubstituteRequestListResponse.from(request, acceptedCandidateMap)
        }
    }

    // 내부적으로 사용하는 수락자 조회 - private으로 캡슐화
    private fun getAcceptedCandidates(requestIds: List<Long>): Map<Long, SubstituteCandidate> {
        if (requestIds.isEmpty()) {
            return emptyMap()
        }
        // 수락자 조회
        val candidates = substituteCandidateRepository
            .findByRequestIdInAndStatus(requestIds, CandidateStatus.ACCEPTED)

        // 그룹화로 수락자가 2명인 경우도 감안
        val grouped = candidates.groupBy { candidate -> candidate.request.id }

        // 수락자가 다수 있을 시 예외
        grouped.forEach { (requestId, matchedCandidates) ->
            if (matchedCandidates.size > 1) {
                log.error(
                    "ACCEPTED 요청(requestId={})에 수락자가 {}명 조회되었습니다. candidateIds={}",
                    requestId,
                    matchedCandidates.size,
                    matchedCandidates.map { candidate -> candidate.id },
                )
                throw BusinessException(SubstituteRequestErrorCode.ACCEPTED_CANDIDATE_DUPLICATED)
            }
        }

        // 요청 == ACCEPTED + 수락자 == null일 때
        val missingRequestIds = requestIds.filterNot { requestId -> requestId in grouped }

        if (missingRequestIds.isNotEmpty()) {
            log.error(
                "ACCEPTED 상태인 요청의 수락자 정보가 누락되었습니다. requestIds={}",
                missingRequestIds,
            )
            throw BusinessException(SubstituteRequestErrorCode.ACCEPTED_CANDIDATE_NOT_FOUND)
        }

        return grouped.mapValues { (_, matchedCandidates) -> matchedCandidates.first() }
    }

    @Transactional
    fun approve(requestId: Long?, actorId: Long?): SubstituteRequest {
        // 대체 근무 요청이 존재하는지
        val request = substituteRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow {
                BusinessException(SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND)
            }

        // request == ACCEPTED 여부 검사
        if (request.status != RequestStatus.ACCEPTED) {
            throw BusinessException(SubstituteRequestErrorCode.INVALID_REQUEST)
        }
        // 근무지 ID 뽑아오기
        val workplaceId = request.shift.schedule.workplace.id
        // 매니저 인가 확인
        workplaceMemberService.requireManager(actorId, workplaceId)

        // 요청의 스케줄이 공개되지 않았을 때
        if (request.shift.schedule.status != ScheduleStatus.PUBLISHED) {
            throw BusinessException(ScheduleErrorCode.NOT_PUBLISHED)
        }
        // 요청의 근무가 취소되었다면
        if (request.shift.status != ShiftStatus.SCHEDULED) {
            throw BusinessException(ShiftErrorCode.IS_CANCELED_SHIFT)
        }
        // 현재 Shift.member != Request.requesterMember
        if (request.requesterMember.id != request.shift.member.id) {
            throw BusinessException(SubstituteRequestErrorCode.INVALID_MEMBER)
        }
        workplaceRepository.findById(workplaceId)
            .orElseThrow { BusinessException(WorkplaceErrorCode.WORKPLACE_NOT_FOUND) }

        val candidate = substituteCandidateRepository
            .findByRequestIdAndStatus(requestId, CandidateStatus.ACCEPTED)
            .orElseThrow { BusinessException(SubstituteRequestErrorCode.NOT_FOUND_CANDIDATE) }

        // 수락자가 해당 근무지 소속인지
        val acceptedMember = candidate.member

        if (
            acceptedMember.workplace.id != workplaceId ||
            acceptedMember.leftAt != null ||
            acceptedMember.role != WorkplaceRole.EMPLOYEE
        ) {
            throw BusinessException(ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE)
        }

        val candidateUserId = acceptedMember.user.id

        userService.getByIdForUpdate(candidateUserId)

        // 현재를 기준으로 대체 근무 시작 시간이 지나거나 같다면 (now >= startAt)
        val validationNow = LocalDateTime.now(clock)

        validateNotStarted(request.shift, validationNow)

        // 혹여나 수락 후 승인 전 사이에 근무를 배정받았는지
        val hasConflictingShift = shiftRepository.existsConflictingShift(
            candidateUserId,
            request.shift.startAt, // 시작 시간
            request.shift.endAt, // 종료 시간
        )
        if (hasConflictingShift) {
            throw BusinessException(SubstituteRequestErrorCode.CONFLICT_SHIFT)
        }

        // 불가능 시간과 중복되는지
        if (
            unavailableTimeRepository.existsOverlappingUnavailableTime(
                candidateUserId,
                request.shift.startAt,
                request.shift.endAt,
            )
        ) {
            throw BusinessException(SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME)
        }
        // 이전에 다른 대체 근무를 수락했다면, 그것과 중복되는지
        if (
            substituteCandidateRepository.existsConflictingActiveSubstitute(
                candidateUserId,
                requestId,
                request.shift.startAt,
                request.shift.endAt,
                validationNow,
            )
        ) {
            throw BusinessException(SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE)
        }
        // 승인 전 시간 재확인
        val approvedAt = LocalDateTime.now(clock)
        validateNotStarted(request.shift, approvedAt)

        val requesterMemberId = request.requesterMember.id

        val acceptedMemberId = acceptedMember.id

        // 최종 승인 시작
        request.approveRequest(acceptedMember, approvedAt)

        publishSubstituteRequestApprovedEvent(request.id, requesterMemberId, acceptedMemberId)

        return request
    }

    private fun validateNotStarted(shift: Shift, now: LocalDateTime) {
        if (shift.startAt <= now) {
            throw BusinessException(ShiftErrorCode.INVALID_TIME_VALUE)
        }
    }

    @Transactional
    fun close(requestId: Long?, actorId: Long?): SubstituteRequest {
        // Request 조회
        val request = substituteRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow {
                BusinessException(SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND)
            }

        // findByIdForUpdate를 통과했으므로 requestId는 null이 아님
        val lockedRequestId = requireNotNull(requestId)

        val shift = request.shift
        val schedule = shift.schedule
        val workplace = schedule.workplace

        // 매니저 인가 검증
        workplaceMemberService.requireManager(actorId, workplace.id)

        val statusBeforeClose = request.status

        // Request 상태 검증. OPEN 또는 ACCEPTED만 종료 가능
        if (statusBeforeClose != RequestStatus.OPEN && statusBeforeClose != RequestStatus.ACCEPTED) {
            throw BusinessException(SubstituteRequestErrorCode.ALREADY_TERMINATED)
        }

        // Schedule/Shift 상태 검증
        if (schedule.status != ScheduleStatus.PUBLISHED) {
            throw BusinessException(ScheduleErrorCode.NOT_PUBLISHED)
        }
        if (shift.status != ShiftStatus.SCHEDULED) {
            throw BusinessException(ShiftErrorCode.INVALID_STATUS_VALUE)
        }

        // Shift 시작 전인지 검증
        val now = LocalDateTime.now(clock)
        if (shift.startAt <= now) {
            throw BusinessException(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED_NOT_CLOSE)
        }

        val requesterMemberId = request.requesterMember.id

        val recipientMemberIds = findManagerClosedRecipientMemberIds(
            lockedRequestId,
            requesterMemberId,
            statusBeforeClose,
        )

        // Request 종료 처리 (Shift.member, Candidate 상태는 변경하지 않음)
        request.closeByManager(now)

        publishSubstituteRequestManagerClosedEvent(lockedRequestId, recipientMemberIds)

        return request
    }

    private fun publishSubstituteRequestCreatedEvent(
        request: SubstituteRequest,
        createdCandidates: List<SubstituteCandidate>,
    ) {
        val candidateMemberIds = createdCandidates.map { candidate -> candidate.member.id }

        applicationEventPublisher.publishEvent(
            SubstituteRequestCreatedEvent(
                requestId = request.id,
                candidateMemberIds = candidateMemberIds,
            ),
        )
    }

    private fun publishSubstituteNoCandidateEvent(shift: Shift) {
        val workplaceId = shift.schedule.workplace.id

        val managerMemberIds = workplaceMemberRepository
            .findAllByWorkplace_IdAndRoleAndLeftAtIsNull(workplaceId, WorkplaceRole.MANAGER)
            .map { member -> member.id }

        applicationEventPublisher.publishEvent(
            SubstituteNoCandidateEvent(
                shiftId = shift.id,
                managerMemberIds = managerMemberIds,
            ),
        )
    }

    private fun publishSubstituteRequestApprovedEvent(
        requestId: Long,
        requesterMemberId: Long,
        acceptedMemberId: Long,
    ) {
        applicationEventPublisher.publishEvent(
            SubstituteRequestApprovedEvent(
                requestId = requestId,
                requesterMemberId = requesterMemberId,
                acceptedMemberId = acceptedMemberId,
            ),
        )
    }

    private fun findManagerClosedRecipientMemberIds(
        requestId: Long,
        requesterMemberId: Long,
        statusBeforeClose: RequestStatus,
    ): List<Long> {
        val candidateMemberIds =
            if (statusBeforeClose == RequestStatus.OPEN) {
                substituteCandidateRepository
                    .findByRequestIdInAndStatus(listOf(requestId), CandidateStatus.PENDING)
                    .map { candidate -> candidate.member.id }
            } else {
                // getAcceptedCandidates가 수락자 누락 시 예외를 던지므로 항상 존재함
                val acceptedCandidate =
                    getAcceptedCandidates(listOf(requestId)).getValue(requestId)

                listOf(acceptedCandidate.member.id)
            }

        return (listOf(requesterMemberId) + candidateMemberIds).distinct()
    }

    private fun publishSubstituteRequestManagerClosedEvent(
        requestId: Long,
        recipientMemberIds: List<Long>,
    ) {
        applicationEventPublisher.publishEvent(
            SubstituteRequestManagerClosedEvent(
                requestId = requestId,
                recipientMemberIds = recipientMemberIds,
            ),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(SubstituteRequestService::class.java)
    }
}
