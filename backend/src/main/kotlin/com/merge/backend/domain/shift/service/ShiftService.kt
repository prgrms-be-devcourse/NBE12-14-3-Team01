package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.dto.ShiftRequest
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.entity.ShiftStatus.SCHEDULED
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.domain.workplace.service.WorkplaceMemberService
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.util.TimeRangeUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShiftService(
    private val scheduleRepository: ScheduleRepository,
    private val shiftRepository: ShiftRepository,
    private val workplaceMemberService: WorkplaceMemberService,
    private val workplaceMemberRepository: WorkplaceMemberRepository,
    private val workplaceRepository: WorkplaceRepository,
    private val unavailableTimeRepository: UnavailableTimeRepository
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

        if (shiftUserId != currentUserId) {
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
