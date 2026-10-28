package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.dto.ShiftRequest
import com.merge.backend.domain.shift.dto.ShiftReassignRequest
import com.merge.backend.domain.shift.dto.PublishedShiftCreateRequest
import com.merge.backend.domain.shift.dto.PublishedShiftConfirmationRequiredResponse
import com.merge.backend.domain.shift.dto.NewShiftKey
import com.merge.backend.domain.shift.dto.NewShiftUnavailableConflictResponse
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftAssignmentHistory
import com.merge.backend.domain.shift.entity.ShiftAssignmentReason
import com.merge.backend.domain.shift.entity.ShiftCancellationReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.entity.ShiftStatus.SCHEDULED
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.event.PublishedShiftChangeKind
import com.merge.backend.domain.shift.event.PublishedShiftChangedEvent
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftAssignmentHistoryRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.substitute.service.ShiftChangeClosedRequest
import com.merge.backend.domain.substitute.service.SubstituteRequestService
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.domain.workplace.service.WorkplaceMemberService
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.dto.UnavailableConflictOverlap
import com.merge.backend.global.dto.UnavailableConflictResponse
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.util.TimeRangeUtils
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShiftService(
    private val scheduleRepository: ScheduleRepository,
    private val shiftRepository: ShiftRepository,
    private val workplaceMemberService: WorkplaceMemberService,
    private val workplaceMemberRepository: WorkplaceMemberRepository,
    private val workplaceRepository: WorkplaceRepository,
    private val unavailableTimeRepository: UnavailableTimeRepository,
    private val substituteRequestService: SubstituteRequestService,
    private val shiftAssignmentHistoryRepository: ShiftAssignmentHistoryRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    @Transactional(readOnly = true)
    fun list(
        weekStartDate: LocalDate,
        currentUserId: Long?
    ): List<Shift> {
        validWeekStartDate(weekStartDate)

        return shiftRepository.findAllByWeekStartDateAndCurrentUserId(
            weekStartDate,
            currentUserId
        )
    }

    @Transactional(readOnly = true)
    fun detail(shiftId: Long?, currentUserId: Long?): Shift {
        val shift = shiftRepository.findById(requireNotNull(shiftId))
            .orElseThrow {
                BusinessException(ShiftErrorCode.NOT_FOUND_SHIFT_ERROR)
            }

        //조회할 근무가 PUBLISHED 되지 않았다면
        if (shift.schedule.status != ScheduleStatus.PUBLISHED) {
            throw BusinessException(ShiftErrorCode.NOT_PUBLISHED_SHIFT)
        }
        //취소된 근무를 조회하려 할 시
        if (shift.status != ShiftStatus.SCHEDULED) {
            throw BusinessException(ShiftErrorCode.IS_CANCELED_SHIFT)
        }

        val shiftUserId = shift.member.user.id

        if (shiftUserId != currentUserId || shift.member.leftAt != null) {
            throw BusinessException(ShiftErrorCode.FORBIDDEN_ACCESS)
        }
        return shift
    }

    @Transactional
    fun create(
        reqBody: ShiftRequest,
        workplaceId: Long?,
        scheduleId: Long?,
        actorId: Long?
    ): Shift {
        //요청 사용자 해당 매장의 MANAGER 권한을 가지고 있는지
        workplaceMemberService.requireManager(actorId, workplaceId)

        //스케줄 관련 예외 검사
        val schedule = validSchedule(scheduleId, workplaceId)

        //근무자 관련 예외 검사
        val member = validWorkplaceMember(workplaceId, reqBody)

        //다른 근무지의 근무 시간까지의 예외 검증을 위해 userId를 갖고옴
        val userId = member.user.id
        //근무 시간 관련 예외 처리
        validShiftTime(reqBody, schedule, member, scheduleId, userId, null)

        //불가능 시간 관련 예외 처리
        validateUnavailableConflictConfirmation(reqBody, userId)

        return saveShift(
            schedule,
            member,
            requireNotNull(reqBody.startAt),
            requireNotNull(reqBody.endAt)
        )
    }

    /**
     * SCH-01 전용 Pattern 기반 초기 Shift 생성.
     *
     * ScheduleService에서 관리자 권한과 주차를 검증하고, 현재 구성원의 Pattern을 해당 주차의 날짜·시간으로 변환한 뒤 호출한다. 편집용 충돌 검증은
     * 적용하지 않으며, 공개 시 SCH-06에서 최종 검증한다.
     */
    @Transactional
    fun createFromValidatedPattern(
        schedule: Schedule,
        member: WorkplaceMember,
        startAt: LocalDateTime,
        endAt: LocalDateTime
    ): Shift = saveShift(
        schedule,
        member,
        startAt,
        endAt
    )

    private fun saveShift(
        schedule: Schedule,
        member: WorkplaceMember,
        startAt: LocalDateTime,
        endAt: LocalDateTime
    ): Shift {
        val shift = Shift(
            schedule,
            member,
            startAt,
            endAt,
            SCHEDULED
        )

        return shiftRepository.save(shift)
    }

    @Transactional
    fun modify(
        workplaceId: Long?,
        scheduleId: Long?,
        shiftId: Long?,
        reqBody: ShiftRequest,
        actorId: Long?
    ): Shift {
        //요청 사용자(actorId)가 해당 매장의 MANAGER 권한을 가지고 있는지 검증
        workplaceMemberService.requireManager(actorId, workplaceId)

        //스케줄 관련 예외 검사
        val schedule = validSchedule(scheduleId, workplaceId)

        //근무 관련 예외 검사
        val shift = validShift(scheduleId, shiftId)

        //근무자 관련 예외 검사
        val member = validWorkplaceMember(workplaceId, reqBody)

        //다른 근무지의 근무 시간까지의 예외 검증을 위해 userId를 갖고옴
        val userId = member.user.id
        //근무 시간 관련 예외 처리
        validShiftTime(reqBody, schedule, member, scheduleId, userId, shift.id)

        //불가능 시간 관련 예외 처리
        validateUnavailableConflictConfirmation(reqBody, userId)

        return shift.update(
            member,
            requireNotNull(reqBody.startAt),
            requireNotNull(reqBody.endAt)
        )
    }

    @Transactional
    fun delete(
        workplaceId: Long?,
        scheduleId: Long?,
        shiftId: Long?,
        actorId: Long?
    ) {
        //요청 사용자(actorId)가 해당 매장의 MANAGER 권한을 가지고 있는지 검증
        workplaceMemberService.requireManager(actorId, workplaceId)

        //근무지가 실제 존재하는지
        val workplace: Workplace = workplaceRepository.findById(requireNotNull(workplaceId))
            .orElseThrow {
                BusinessException(ShiftErrorCode.NOT_FOUND_WORKPLACE_ERROR)
            }
        //스케줄 관련 예외 검사
        val schedule = validSchedule(scheduleId, workplaceId)
        //근무 관련 예외 검사
        val shift = validShift(scheduleId, shiftId)

        shiftRepository.deleteById(requireNotNull(shiftId))
    }

    @Transactional
    fun createPublishedShift(
        workplaceId: Long?,
        scheduleId: Long?,
        request: PublishedShiftCreateRequest,
        actorId: Long?,
    ): Shift {
        val actorMember = workplaceMemberService.requireManager(actorId, workplaceId)
        val schedule = validPublishedSchedule(scheduleId, workplaceId)
        val memberId = requirePublishedShiftCreateInput(request.memberId)
        val startAt = requirePublishedShiftCreateInput(request.startAt)
        val endAt = requirePublishedShiftCreateInput(request.endAt)

        validateNewShiftAcknowledgementKeys(schedule.id, memberId, startAt, endAt, request)
        validatePublishedShiftTime(schedule, startAt, endAt)
        validatePublishedShiftNotStarted(startAt)

        val targetScope = workplaceMemberRepository.findScopeById(memberId)
            ?: throw BusinessException(ShiftErrorCode.NOT_FOUND_ERROR)
        if (targetScope.workplaceId != workplaceId) {
            throw BusinessException(ShiftErrorCode.NOT_FOUND_ERROR)
        }

        val targetUser = userRepository.findByIdForUpdate(targetScope.userId)
            .orElseThrow { BusinessException(ShiftErrorCode.NOT_FOUND_ERROR) }
        val targetMember = workplaceMemberRepository.findByIdForShare(memberId)
            .orElseThrow { BusinessException(ShiftErrorCode.NOT_FOUND_ERROR) }
        if (targetMember.workplace.id != workplaceId || targetMember.leftAt != null ||
            targetMember.user.id != targetUser.id
        ) {
            throw BusinessException(ShiftErrorCode.NOT_FOUND_ERROR)
        }

        validatePublishedShiftOverlap(scheduleId, targetMember.id, targetUser.id, startAt, endAt)
        validateNewShiftUnavailableConflictAcknowledgement(schedule.id, targetMember, startAt, endAt, request)
        validatePublishedShiftNotStarted(startAt)

        val shift = saveShift(schedule, targetMember, startAt, endAt)

        // 새 담당에게 SHIFT_ASSIGNED (받는 사람·문구는 알림 쪽에서 정함)
        publishPublishedShiftChanged(PublishedShiftChangeKind.ADDED, shift, null, targetMember, actorMember, emptyList())

        return shift
    }

    @Transactional
    fun cancelPublishedShift(
        workplaceId: Long?,
        scheduleId: Long?,
        shiftId: Long?,
        actorId: Long?,
    ): PublishedShiftCancellationResult {
        val actorMember = workplaceMemberService.requireManager(actorId, workplaceId)
        val schedule = validPublishedSchedule(scheduleId, workplaceId)
        val shift = validPublishedShift(schedule, shiftId)
        val now = LocalDateTime.now(clock)
        val closedRequests = substituteRequestService.closeActiveRequestsByShiftChange(
            requireNotNull(shift.id),
            now,
        )

        shift.cancel(
            ShiftCancellationReason.MANAGER_CANCELLED,
            now,
            actorMember,
        )

        publishPublishedShiftChanged(PublishedShiftChangeKind.CANCELLED, shift, shift.member, null, actorMember, closedRequests)

        return PublishedShiftCancellationResult(shift, closedRequests.size)
    }

    @Transactional
    fun reassignPublishedShift(
        workplaceId: Long?,
        scheduleId: Long?,
        shiftId: Long?,
        request: ShiftReassignRequest,
        actorId: Long?,
    ): PublishedShiftReassignmentResult {
        val actorMember = workplaceMemberService.requireManager(actorId, workplaceId)
        val schedule = validPublishedSchedule(scheduleId, workplaceId)
        val shift = validPublishedShift(schedule, shiftId)
        val targetMemberId = requireNotNull(request.targetMemberId)

        if (shift.member.id == targetMemberId) {
            throw BusinessException(ShiftErrorCode.SAME_SHIFT_MEMBER_REASSIGNMENT)
        }
        validateAcknowledgedUnavailableConflictTargets(shift, targetMemberId, request)

        val targetScope = workplaceMemberRepository.findScopeById(targetMemberId)
            ?: throw BusinessException(ShiftErrorCode.NOT_FOUND_ERROR)
        if (targetScope.workplaceId != workplaceId) {
            throw BusinessException(ShiftErrorCode.NOT_FOUND_ERROR)
        }

        val now = LocalDateTime.now(clock)
        val closedRequests = substituteRequestService.closeActiveRequestsByShiftChange(
            requireNotNull(shift.id),
            now,
        )

        val targetUser = userRepository.findByIdForUpdate(targetScope.userId)
            .orElseThrow { BusinessException(ShiftErrorCode.NOT_FOUND_ERROR) }
        val targetMember = workplaceMemberRepository.findByIdForShare(targetMemberId)
            .orElseThrow { BusinessException(ShiftErrorCode.NOT_FOUND_ERROR) }

        if (targetMember.workplace.id != workplaceId || targetMember.leftAt != null ||
            targetMember.user.id != targetUser.id
        ) {
            throw BusinessException(ShiftErrorCode.NOT_FOUND_ERROR)
        }

        validateShiftOverlap(
            scheduleId,
            targetMember.id,
            targetUser.id,
            shift.startAt,
            shift.endAt,
            shift.id,
        )
        validateUnavailableConflictAcknowledgement(shift, targetMember, request)

        val previousMember = shift.member
        shift.changeMember(targetMember)
        shiftAssignmentHistoryRepository.save(
            ShiftAssignmentHistory(
                shift,
                previousMember,
                targetMember,
                ShiftAssignmentReason.MANAGER_REASSIGNED,
                actorMember,
            ),
        )

        publishPublishedShiftChanged(
            PublishedShiftChangeKind.REASSIGNED, shift, previousMember, targetMember, actorMember, closedRequests,
        )

        return PublishedShiftReassignmentResult(shift, closedRequests.size)
    }

    // 공개 후 근무 변경 알림 (NOTIFICATION-TYPES §3). 업무 Transaction 안에서 발행하고 저장은 Commit 후
    private fun publishPublishedShiftChanged(
        kind: PublishedShiftChangeKind,
        shift: Shift,
        previousMember: WorkplaceMember?,
        newMember: WorkplaceMember?,
        actorMember: WorkplaceMember,
        closedRequests: List<ShiftChangeClosedRequest>,
    ) {
        applicationEventPublisher.publishEvent(
            PublishedShiftChangedEvent(
                kind = kind,
                shiftId = shift.id,
                previousMemberId = previousMember?.id,
                newMemberId = newMember?.id,
                actorMemberId = actorMember.id,
                closedRequests = closedRequests,
            ),
        )
    }

    //스케줄 관련 예외 검사 메서드
    private fun validSchedule(scheduleId: Long?, workplaceId: Long?): Schedule {
        //스케줄 존재 확인
        val schedule = scheduleRepository.findByIdForUpdate(scheduleId)
            .orElseThrow {
                BusinessException(
                    ShiftErrorCode.NOT_FOUND_SCHEDULE_ERROR
                )
            }
        //스케줄이 해당 workplace 소속인지 확인
        if (schedule.workplace.id != workplaceId) {
            throw BusinessException(ShiftErrorCode.INVALID_WORKPLACE_VALUE)
        }
        //스케줄이 DRAFT인지 확인
        if (schedule.status != ScheduleStatus.DRAFT) {
            throw BusinessException(ShiftErrorCode.INVALID_STATUS_VALUE)
        }
        return schedule
    }

    private fun validPublishedSchedule(scheduleId: Long?, workplaceId: Long?): Schedule {
        val schedule = scheduleRepository.findByIdForUpdate(scheduleId)
            .orElseThrow {
                BusinessException(ShiftErrorCode.NOT_FOUND_SCHEDULE_ERROR)
            }

        if (schedule.workplace.id != workplaceId) {
            throw BusinessException(ShiftErrorCode.NOT_FOUND_SCHEDULE_ERROR)
        }
        if (schedule.status != ScheduleStatus.PUBLISHED) {
            throw BusinessException(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        }

        return schedule
    }

    private fun validPublishedShift(schedule: Schedule, shiftId: Long?): Shift {
        val shift = shiftRepository.findByIdForUpdate(shiftId)
            .orElseThrow {
                BusinessException(ShiftErrorCode.NOT_FOUND_SHIFT_ERROR)
            }

        if (shift.schedule.id != schedule.id) {
            throw BusinessException(ShiftErrorCode.NOT_FOUND_SHIFT_ERROR)
        }
        if (shift.status != ShiftStatus.SCHEDULED || !shift.startAt.isAfter(LocalDateTime.now(clock))) {
            throw BusinessException(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        }

        return shift
    }

    private fun validShift(scheduleId: Long?, shiftId: Long?): Shift {
        //shift 존재 확인
        val shift = shiftRepository.findById(requireNotNull(shiftId))
            .orElseThrow {
                BusinessException(ShiftErrorCode.NOT_FOUND_ERROR)
            }
        //shift가 해당 schedule 소속인지
        if (shift.schedule.id != scheduleId) {
            throw BusinessException(ShiftErrorCode.INVALID_SHIFT_VALUE)
        }
        return shift
    }

    private fun validWorkplaceMember(
        workplaceId: Long?,
        reqBody: ShiftRequest
    ): WorkplaceMember {
        //WorkplaceMember 존재 확인
        val member = workplaceMemberRepository.findById(requireNotNull(reqBody.memberId))
            .orElseThrow {
                BusinessException(
                    ShiftErrorCode.NOT_FOUND_ERROR
                )
            }
        //member가 현 workplace의 일원인지 체크 && 퇴사하지 않았는지
        if (member.workplace.id != workplaceId || member.leftAt != null) {
            throw BusinessException(ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE)
        }
        return member
    }

    private fun validShiftTime(
        reqBody: ShiftRequest,
        schedule: Schedule,
        member: WorkplaceMember,
        scheduleId: Long?,
        userId: Long?,
        currentShiftId: Long?
    ) {
        val startAt = requireNotNull(reqBody.startAt)
        val endAt = requireNotNull(reqBody.endAt)

        //p0에서는 자정을 넘어가는 근무는 존재하지 않는다.
        if (startAt.toLocalDate() != endAt.toLocalDate()) {
            throw BusinessException(ShiftErrorCode.INVALID_SHIFT_SAME_DAY)
        }
        //startAt < endAt
        if (!TimeRangeUtils.isValidRange(
                startAt,
                endAt
            )
        ) {
            throw BusinessException(
                ShiftErrorCode.INVALID_BEFORE_AFTER_VALUE
            )
        }
        //Schedule 주차 범위 검증 추가
        validateShiftInScheduleWeek(schedule, startAt, endAt)

        //시간 중복(Overlap) 3단계 검증
        validateShiftOverlap(
            scheduleId,
            member.id,
            userId,
            startAt,
            endAt,
            currentShiftId
        )
    }

    // UnavailableTime 충돌에 대한 관리자 확인 여부 검증
    private fun validateUnavailableConflictConfirmation(
        reqBody: ShiftRequest,
        userId: Long?
    ) {
        // confirmUnavailableConflict가 false일 때만 체크)
        if (!requireNotNull(reqBody.confirmUnavailableConflict)) {
            if (unavailableTimeRepository.existsOverlappingUnavailableTime(
                    userId,
                    requireNotNull(reqBody.startAt),
                    requireNotNull(reqBody.endAt)
                )
            ) {
                // 변경을 적용하지 않고, 관리자 확인이 필요한 Warning을 발생시킨다.
                throw BusinessException(
                    ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT,
                    ConfirmationRequiredResponse(true)
                )
            }
        }
    }

    private fun validateUnavailableConflictAcknowledgement(
        shift: Shift,
        targetMember: WorkplaceMember,
        request: ShiftReassignRequest,
    ) {
        val currentOverlaps = normalizeUnavailableOverlaps(
            targetMember.user.id,
            shift.startAt,
            shift.endAt,
        )
        if (currentOverlaps.isEmpty()) {
            return
        }

        val acknowledgedOverlaps = normalizeOverlaps(
            requireNotNull(request.acknowledgedUnavailableConflicts)
                .asSequence()
                .filter { it.shiftId == shift.id && it.targetMemberId == targetMember.id }
                .flatMap { requireNotNull(it.overlaps).asSequence() }
                .map { overlap ->
                    UnavailableConflictOverlap(
                        requireNotNull(overlap.startAt),
                        requireNotNull(overlap.endAt),
                    )
                }
                .toList(),
        )

        if (currentOverlaps.all { current ->
                acknowledgedOverlaps.any { acknowledged ->
                    !acknowledged.startAt.isAfter(current.startAt) &&
                        !acknowledged.endAt.isBefore(current.endAt)
                }
            }
        ) {
            return
        }

        throw BusinessException(
            ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT_CONFIRMATION_REQUIRED,
            ConfirmationRequiredResponse(
                requiresConfirmation = true,
                unavailableConflicts = listOf(
                    UnavailableConflictResponse(
                        shift.id,
                        shift.startAt,
                        shift.endAt,
                        targetMember.id,
                        targetMember.user.name,
                        currentOverlaps,
                    ),
                ),
            ),
        )
    }

    private fun validateAcknowledgedUnavailableConflictTargets(
        shift: Shift,
        targetMemberId: Long,
        request: ShiftReassignRequest,
    ) {
        val acknowledgedConflicts = requireNotNull(request.acknowledgedUnavailableConflicts)

        if (acknowledgedConflicts.any {
                it.shiftId != shift.id || it.targetMemberId != targetMemberId
            } || acknowledgedConflicts.asSequence()
                .flatMap { requireNotNull(it.overlaps).asSequence() }
                .any { overlap ->
                    val startAt = overlap.startAt
                    val endAt = overlap.endAt
                    startAt == null || endAt == null || !startAt.isBefore(endAt)
                }
        ) {
            throw BusinessException(ShiftErrorCode.INVALID_INPUT_VALUE)
        }
    }

    private fun validateNewShiftAcknowledgementKeys(
        scheduleId: Long?,
        memberId: Long,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        request: PublishedShiftCreateRequest,
    ) {
        val conflicts = requirePublishedShiftCreateInput(request.acknowledgedUnavailableConflicts)
        if (conflicts.any { conflict ->
                val key = conflict.newShiftKey
                key == null || key.scheduleId != scheduleId || key.targetMemberId != memberId ||
                    key.shiftStartAt != startAt || key.shiftEndAt != endAt ||
                    requirePublishedShiftCreateInput(conflict.overlaps).any { overlap ->
                        val overlapStartAt = overlap.startAt
                        val overlapEndAt = overlap.endAt
                        overlapStartAt == null || overlapEndAt == null ||
                            !overlapStartAt.isBefore(overlapEndAt)
                    }
            }
        ) {
            throw BusinessException(ShiftErrorCode.INVALID_INPUT_VALUE)
        }
    }

    private fun validatePublishedShiftTime(
        schedule: Schedule,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
    ) {
        if (startAt.toLocalDate() != endAt.toLocalDate()) {
            throw BusinessException(ShiftErrorCode.INVALID_SHIFT_SAME_DAY)
        }
        if (!TimeRangeUtils.isValidRange(startAt, endAt)) {
            throw BusinessException(ShiftErrorCode.INVALID_BEFORE_AFTER_VALUE)
        }
        validateShiftInScheduleWeek(schedule, startAt, endAt)
    }

    private fun validatePublishedShiftNotStarted(startAt: LocalDateTime) {
        if (!startAt.isAfter(LocalDateTime.now(clock))) {
            throw BusinessException(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        }
    }

    private fun validatePublishedShiftOverlap(
        scheduleId: Long?,
        memberId: Long?,
        userId: Long?,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
    ) {
        if (shiftRepository.existsOverlappingInSchedule(scheduleId, memberId, startAt, endAt, null)) {
            throw BusinessException(ShiftErrorCode.DUPLICATE_LOCAL_SCHEDULE_TIME)
        }
        if (shiftRepository.existsOverlappingOfficialShift(
                userId, ScheduleStatus.PUBLISHED, startAt, endAt, null,
            )
        ) {
            throw BusinessException(ShiftErrorCode.DUPLICATE_GLOBAL_SCHEDULE_TIME)
        }
    }

    private fun validateNewShiftUnavailableConflictAcknowledgement(
        scheduleId: Long?,
        targetMember: WorkplaceMember,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        request: PublishedShiftCreateRequest,
    ) {
        val currentOverlaps = normalizeUnavailableOverlaps(targetMember.user.id, startAt, endAt)
        if (currentOverlaps.isEmpty()) return

        val acknowledgedOverlaps = normalizeOverlaps(
            requirePublishedShiftCreateInput(request.acknowledgedUnavailableConflicts)
                .asSequence()
                .flatMap { requirePublishedShiftCreateInput(it.overlaps).asSequence() }
                .map { overlap ->
                    UnavailableConflictOverlap(
                        requirePublishedShiftCreateInput(overlap.startAt),
                        requirePublishedShiftCreateInput(overlap.endAt),
                    )
                }
                .toList(),
        )
        if (currentOverlaps.all { current ->
                acknowledgedOverlaps.any { acknowledged ->
                    !acknowledged.startAt.isAfter(current.startAt) &&
                        !acknowledged.endAt.isBefore(current.endAt)
                }
            }
        ) return

        throw BusinessException(
            ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT_CONFIRMATION_REQUIRED,
            PublishedShiftConfirmationRequiredResponse(
                requiresConfirmation = true,
                unavailableConflicts = listOf(
                    NewShiftUnavailableConflictResponse(
                        NewShiftKey(scheduleId, targetMember.id, startAt, endAt),
                        targetMember.user.name,
                        currentOverlaps,
                    ),
                ),
            ),
        )
    }

    private fun <T> requirePublishedShiftCreateInput(value: T?): T = value
        ?: throw BusinessException(ShiftErrorCode.INVALID_INPUT_VALUE)

    private fun normalizeUnavailableOverlaps(
        userId: Long?,
        shiftStartAt: LocalDateTime,
        shiftEndAt: LocalDateTime,
    ): List<UnavailableConflictOverlap> {
        val overlaps = unavailableTimeRepository.findByUserId(userId)
            .mapNotNull { unavailableTime ->
                val startAt = maxOf(unavailableTime.startAt, shiftStartAt)
                val endAt = minOf(unavailableTime.endAt, shiftEndAt)
                if (startAt.isBefore(endAt)) {
                    UnavailableConflictOverlap(startAt, endAt)
                } else {
                    null
                }
            }
            .sortedBy { it.startAt }

        return normalizeOverlaps(overlaps)
    }

    private fun normalizeOverlaps(
        overlaps: List<UnavailableConflictOverlap>,
    ): List<UnavailableConflictOverlap> = overlaps
        .sortedBy { it.startAt }
        .fold(mutableListOf()) { normalized, overlap ->
            val previous = normalized.lastOrNull()
            if (previous == null || overlap.startAt.isAfter(previous.endAt)) {
                normalized += overlap
            } else {
                normalized[normalized.lastIndex] = UnavailableConflictOverlap(
                    previous.startAt,
                    maxOf(previous.endAt, overlap.endAt),
                )
            }
            normalized
        }

    /**
     * Shift의 근무 시간이 Schedule의 주차 범위 내에 들어오는지 검증
     */
    private fun validateShiftInScheduleWeek(
        schedule: Schedule,
        startAt: LocalDateTime,
        endAt: LocalDateTime
    ) {
        val weekStartDate = schedule.weekStartDate // 예: 2026-09-21

        // 주차 범위 계산 [월요일 00:00:00, 다음주 월요일 00:00:00)
        val scheduleStart = weekStartDate.atStartOfDay()
        // 2026-09-21T00:00:00
        val scheduleEnd = weekStartDate.plusWeeks(1).atStartOfDay()
        // 2026-09-28T00:00:00

        // startAt이 scheduleStart보다 앞서거나, endAt이 scheduleEnd보다 뒤에 있으면 예외 발생
        if (startAt.isBefore(scheduleStart) || endAt.isAfter(scheduleEnd)) {
            throw BusinessException(ShiftErrorCode.INVALID_SHIFT_INSCHEDULE)
        }
    }

    //Shift 시간 중복 (Overlap) 검증 메서드
    private fun validateShiftOverlap(
        scheduleId: Long?,
        memberId: Long?,
        userId: Long?,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        currentShiftId: Long?
    ) {
        // 1) 동일 Schedule 내 동일 Member 중복 체크
        if (shiftRepository.existsOverlappingInSchedule(
                scheduleId,
                memberId,
                startAt,
                endAt,
                currentShiftId
            )
        ) {
            throw BusinessException(ShiftErrorCode.DUPLICATE_LOCAL_SCHEDULE_TIME)
        }

        // 2) 해당 User의 모든 Workplace 확정 Shift 중복 체크
        if (shiftRepository.existsOverlappingOfficialShift(
                userId,
                ScheduleStatus.PUBLISHED,
                startAt,
                endAt,
                currentShiftId
            )
        ) {
            throw BusinessException(ShiftErrorCode.DUPLICATE_GLOBAL_SCHEDULE_TIME)
        }
    }

    private fun validWeekStartDate(weekStartDate: LocalDate) {
        if (weekStartDate.dayOfWeek != DayOfWeek.MONDAY) {
            throw BusinessException(
                ShiftErrorCode.INVALID_INPUT_VALUE
            )
        }
    }
}
