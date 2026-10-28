package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.dto.ManagerScheduleResponse
import com.merge.backend.domain.shift.dto.ScheduleCreateResponse
import com.merge.backend.domain.shift.dto.SchedulePublishResponse
import com.merge.backend.domain.shift.entity.RegularShiftPattern
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.event.SchedulePublishedEvent
import com.merge.backend.domain.shift.exception.ScheduleErrorCode
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.RegularShiftPatternRepository
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.domain.workplace.service.WorkplaceMemberService
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.util.TimeRangeUtils
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository

@Service
class ScheduleService(
    private val scheduleRepository: ScheduleRepository,
    private val regularShiftPatternRepository: RegularShiftPatternRepository,
    private val shiftRepository: ShiftRepository,
    private val userRepository: UserRepository,
    private val shiftService: ShiftService,
    private val workplaceMemberService: WorkplaceMemberService,
    private val unavailableTimeRepository: UnavailableTimeRepository,
    private val workplaceRepository: WorkplaceRepository,
    private val workplaceMemberRepository: WorkplaceMemberRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val clock: Clock
) {

    @Transactional
    fun createDraftSchedule(
        actorUserId: Long?,
        workplaceId: Long?,
        weekStartDate: LocalDate
    ): ScheduleCreateResponse {
        workplaceMemberService.requireManager(
            actorUserId,
            workplaceId
        )

        validateWeekStartDate(weekStartDate)

        val workplace: Workplace =
            workplaceRepository
                .findByIdForUpdate(workplaceId)
                .orElseThrow {
                    BusinessException(
                        WorkplaceErrorCode.WORKPLACE_NOT_FOUND
                    )
                }

        validateScheduleNotExists(
            workplaceId,
            weekStartDate
        )

        val schedule =
            Schedule(
                workplace,
                weekStartDate
            )

        val savedSchedule =
            scheduleRepository.save(schedule)

        val patterns: List<RegularShiftPattern> =
            regularShiftPatternRepository
                .findByMemberWorkplaceIdAndMemberLeftAtIsNull(
                    workplaceId
                )

        // 소속 Guard (ML-5 §5-1, ML-2-INV-02): Workplace Lock 뒤 대상 소속을 ID 오름차순으로 FOR SHARE로 잠그고
        // 최신 leftAt으로 다시 거른다. 그 사이 퇴사가 Commit된 사람의 Pattern은 복사하지 않는다 ("현재 구성원의 Pattern")
        val currentMemberIds = patterns
            .map { requireNotNull(it.member.id) }
            .distinct()
            .sorted()
            .filter { memberId ->
                workplaceMemberRepository.countCurrentMemberForShare(memberId, requireNotNull(workplaceId)) > 0
            }
            .toSet()
        val currentPatterns = patterns.filter { it.member.id in currentMemberIds }

        for (pattern in currentPatterns) {
            val shiftDate =
                savedSchedule.resolveDate(
                    pattern.dayOfWeek
                )
            val startAt =
                shiftDate.atTime(
                    pattern.startTime
                )

            val endAt =
                shiftDate.atTime(
                    pattern.endTime
                )

            shiftService.createFromValidatedPattern(
                savedSchedule,
                pattern.member,
                startAt,
                endAt
            )
        }

        return ScheduleCreateResponse.from(
            savedSchedule,
            currentPatterns.size
        )
    }

    @Transactional(readOnly = true)
    fun getWeeklySchedule(
        actorUserId: Long?,
        workplaceId: Long?,
        weekStartDate: LocalDate
    ): ManagerScheduleResponse? {
        workplaceMemberService.requireManager(
            actorUserId,
            workplaceId
        )

        validateWeekStartDate(
            weekStartDate
        )

        val optionalSchedule: Optional<Schedule> =
            scheduleRepository
                .findByWorkplace_IdAndWeekStartDate(
                    workplaceId,
                    weekStartDate
                )

        if (optionalSchedule.isEmpty) {
            return null
        }

        val schedule =
            optionalSchedule.get()

        val shifts: List<Shift> =
            shiftRepository
                .findBySchedule_IdOrderByStartAtAscIdAsc(
                    schedule.id
                )

        return ManagerScheduleResponse.from(
            schedule,
            shifts
        )
    }

    @Transactional
    fun publishSchedule(
        actorUserId: Long?,
        workplaceId: Long?,
        scheduleId: Long?,
        confirmUnavailableConflict: Boolean
    ): SchedulePublishResponse {
        val schedule =
            getPublishTargetSchedule(
                actorUserId,
                workplaceId,
                scheduleId
            )

        validateDraftSchedule(
            schedule
        )

        val shifts =
            getPublishTargetShifts(
                schedule
            )

        validatePublishScheduleBeforeUserLock(
            schedule,
            shifts,
            workplaceId
        )

        lockShiftUsers(
            shifts
        )

        validatePublishScheduleAfterUserLock(
            shifts,
            confirmUnavailableConflict
        )

        publishScheduleState(
            schedule
        )

        publishSchedulePublishedEvent(
            schedule,
            shifts
        )

        return SchedulePublishResponse.from(
            schedule
        )
    }

    private fun validateWeekStartDate(
        weekStartDate: LocalDate
    ) {
        if (weekStartDate.dayOfWeek != DayOfWeek.MONDAY) {
            throw BusinessException(
                ScheduleErrorCode.INVALID_WEEK_START_DATE
            )
        }
    }

    private fun validateScheduleNotExists(
        workplaceId: Long?,
        weekStartDate: LocalDate
    ) {
        val existingSchedule: Optional<Schedule> =
            scheduleRepository
                .findByWorkplaceAndWeekStartDateForUpdate(
                    workplaceId,
                    weekStartDate
                )

        if (existingSchedule.isPresent) {
            throw BusinessException(
                ScheduleErrorCode.SCHEDULE_ALREADY_EXISTS
            )
        }
    }

    private fun getScheduleForUpdateOrThrow(
        scheduleId: Long?
    ): Schedule =
        scheduleRepository
            .findByIdForUpdate(scheduleId)
            .orElseThrow {
                BusinessException(
                    ScheduleErrorCode.SCHEDULE_NOT_FOUND
                )
            }

    private fun validateScheduleWorkplace(
        schedule: Schedule,
        workplaceId: Long?
    ) {
        if (schedule.workplace.id != workplaceId) {
            throw BusinessException(
                ScheduleErrorCode.SCHEDULE_NOT_FOUND)
        }
    }

    private fun getPublishTargetSchedule(
        actorUserId: Long?,
        workplaceId: Long?,
        scheduleId: Long?
    ): Schedule {
        workplaceMemberService.requireManager(
            actorUserId,
            workplaceId
        )

        val schedule =
            getScheduleForUpdateOrThrow(scheduleId)

        validateScheduleWorkplace(
            schedule,
            workplaceId
        )

        return schedule
    }

    private fun validateDraftSchedule(
        schedule: Schedule
    ) {
        if (schedule.status != ScheduleStatus.DRAFT) {
            throw BusinessException(
                ScheduleErrorCode.SCHEDULE_NOT_DRAFT
            )
        }
    }

    private fun getPublishTargetShifts(
        schedule: Schedule
    ): List<Shift> {
        val shifts =
            shiftRepository
                .findBySchedule_IdOrderByStartAtAscIdAsc(
                    schedule.id
                )

        if (shifts.isEmpty()) {
            throw BusinessException(
                ScheduleErrorCode.EMPTY_SCHEDULE
            )
        }

        return shifts
    }

    private fun validateCurrentShiftMembers(
        shifts: List<Shift>,
        workplaceId: Long?
    ) {
        for (shift in shifts) {
            val member: WorkplaceMember =
                shift.member

            if (member.workplace.id != workplaceId
                || member.leftAt != null) {
                throw BusinessException(
                    ScheduleErrorCode.INVALID_SHIFT_MEMBER
                )
            }
        }
    }

    private fun validateShiftTimes(
        shifts: List<Shift>
    ) {
        for (shift in shifts) {
            if (!TimeRangeUtils.isValidRange(
                    shift.startAt,
                    shift.endAt
                )
            ) {
                throw BusinessException(
                    ScheduleErrorCode.INVALID_SHIFT_TIME
                )
            }

            if (shift.startAt
                    .toLocalDate()
                != shift.endAt
                    .toLocalDate()
            ) {
                throw BusinessException(
                    ScheduleErrorCode.INVALID_SHIFT_SAME_DAY
                )
            }
        }
    }

    private fun validateShiftWeekRange(
        schedule: Schedule,
        shifts: List<Shift>
    ) {
        val scheduleStart =
            schedule.weekStartDate
                .atStartOfDay()

        val scheduleEnd =
            schedule.weekStartDate
                .plusWeeks(1)
                .atStartOfDay()

        for (shift in shifts) {
            if (shift.startAt
                    .isBefore(scheduleStart)
                || shift.endAt
                    .isAfter(scheduleEnd)
            ) {
                throw BusinessException(
                    ScheduleErrorCode.INVALID_SHIFT_WEEK
                )
            }
        }
    }

    private fun validateInternalShiftOverlap(
        shifts: List<Shift>
    ) {
        for (i in shifts.indices) {
            val first = shifts[i]

            for (j in i + 1 until shifts.size) {
                val second = shifts[j]

                if (first.member
                        .id
                    != second.member
                        .id
                ) {
                    continue
                }

                val overlaps =
                    first.startAt
                        .isBefore(second.endAt)
                    && second.startAt
                        .isBefore(first.endAt)

                if (overlaps) {
                    throw BusinessException(
                        ScheduleErrorCode.SHIFT_OVERLAP
                    )
                }
            }
        }
    }

    private fun validateOfficialShiftConflicts(
        shifts: List<Shift>
    ) {
        for (shift in shifts) {
            val userId =
                shift.member
                    .user
                    .id

            val hasConflict =
                shiftRepository
                    .existsOverlappingOfficialShift(
                        userId,
                        ScheduleStatus.PUBLISHED,
                        shift.startAt,
                        shift.endAt,
                        null
                    )

            if (hasConflict) {
                throw BusinessException(
                    ScheduleErrorCode.OFFICIAL_SHIFT_CONFLICT
                )
            }
        }
    }

    private fun hasUnavailableConflict(
        shifts: List<Shift>
    ): Boolean {
        for (shift in shifts) {
            val userId =
                shift.member
                    .user
                    .id

            val hasConflict =
                unavailableTimeRepository
                    .existsOverlappingUnavailableTime(
                        userId,
                        shift.startAt,
                        shift.endAt
                    )

            if (hasConflict) {
                return true
            }
        }

        return false
    }

    private fun validateUnavailableConflictConfirmation(
        shifts: List<Shift>,
        confirmUnavailableConflict: Boolean
    ) {
        val hasConflict =
            hasUnavailableConflict(
                shifts
            )

        if (hasConflict
            && !confirmUnavailableConflict
        ) {
            throw BusinessException(
                ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT,
                ConfirmationRequiredResponse(true)
            )
        }
    }

    private fun validatePublishScheduleBeforeUserLock(
        schedule: Schedule,
        shifts: List<Shift>,
        workplaceId: Long?
    ) {
        validateCurrentShiftMembers(
            shifts,
            workplaceId
        )

        validateShiftTimes(
            shifts
        )

        validateShiftWeekRange(
            schedule,
            shifts
        )

        validateInternalShiftOverlap(
            shifts
        )
    }

    private fun validatePublishScheduleAfterUserLock(
        shifts: List<Shift>,
        confirmUnavailableConflict: Boolean
    ) {
        validateOfficialShiftConflicts(
            shifts
        )

        validateUnavailableConflictConfirmation(
            shifts,
            confirmUnavailableConflict
        )
    }

    private fun publishScheduleState(
        schedule: Schedule
    ) {
        val publishedAt =
            LocalDateTime.now(clock)

        schedule.publish(
            publishedAt
        )
    }

    private fun lockShiftUsers(
        shifts: List<Shift>
    ) {
        val userIds =
            shifts.stream()
                .map { shift ->
                    shift.member
                        .user
                        .id
                }
                .distinct()
                .sorted()
                .toList()

        for (userId in userIds) {
            userRepository
                .findByIdForUpdate(userId)
                .orElseThrow {
                    IllegalStateException(
                        "Shift 담당 User를 찾을 수 없습니다."
                    )
                }
        }
    }

    private fun getNotificationRecipientMemberIds(
        shifts: List<Shift>
    ): List<Long> =
        shifts.stream()
            .map { shift ->
                requireNotNull(
                    shift.member
                        .id
                )
            }
            .distinct()
            .sorted()
            .toList()

    private fun publishSchedulePublishedEvent(
        schedule: Schedule,
        shifts: List<Shift>
    ) {
        val recipientMemberIds =
            getNotificationRecipientMemberIds(
                shifts
            )

        val event =
            SchedulePublishedEvent(
                schedule.id,
                recipientMemberIds
            )

        applicationEventPublisher.publishEvent(
            event
        )
    }
}
