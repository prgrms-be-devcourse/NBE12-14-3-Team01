package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.UnavailableTime
import com.merge.backend.domain.shift.exception.UnavailableTimeErrorCode
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.rq.Rq
import com.merge.backend.global.util.TimeRangeUtils
import java.time.Clock
import java.time.LocalDateTime
import java.util.HashSet
import kotlin.jvm.JvmRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UnavailableTimeService(
    private val unavailableTimeRepository: UnavailableTimeRepository,
    private val shiftRepository: ShiftRepository,
    private val rq: Rq,
    private val clock: Clock,
    private val userService: UserService
) {

    @JvmRecord
    data class UnavailableTimeResult(
        val unavailableTimeId: Long?,
        val startAt: LocalDateTime,
        val endAt: LocalDateTime,
        val officialShiftConflict: Boolean
    )

    @Transactional(readOnly = true)
    fun findAll(): List<UnavailableTimeResult> {
        val userId = rq.actorId

        val now = LocalDateTime.now(clock)

        val unavailableTimes =
            unavailableTimeRepository
                .findByUserIdAndEndAtAfterOrderByStartAtAsc(
                    userId,
                    now
                )

        if (unavailableTimes.isEmpty()) {
            return emptyList()
        }

        val officialShiftConflictIds =
            HashSet(
                unavailableTimeRepository
                    .findIdsWithOfficialShiftConflict(
                        userId,
                        now
                    )
            )

        return unavailableTimes.stream()
            .map { unavailableTime ->
                UnavailableTimeResult(
                    unavailableTime.id,
                    unavailableTime.startAt,
                    unavailableTime.endAt,
                    officialShiftConflictIds.contains(
                        unavailableTime.id
                    )
                )
            }
            .toList()
    }

    fun register(
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        confirmOfficialShiftConflict: Boolean?
    ): UnavailableTime {
        val userId = rq.actorId

        val user: User =
            userService.getByIdForUpdate(userId)

        val now = LocalDateTime.now(clock)

        validateTimeRange(startAt, endAt)
        validateFutureTime(startAt, now)
        validateUnavailableTimeOverlap(
            userId,
            null,
            startAt,
            endAt
        )
        validateOfficialShiftConflict(
            userId,
            startAt,
            endAt,
            confirmOfficialShiftConflict
        )

        val newUnavailableTime = UnavailableTime(
            user,
            startAt,
            endAt
        )

        return unavailableTimeRepository.save(newUnavailableTime)
    }

    fun update(
        unavailableTimeId: Long?,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        confirmOfficialShiftConflict: Boolean?
    ): UnavailableTime {
        val userId = rq.actorId

        userService.getByIdForUpdate(
            userId
        )

        val now = LocalDateTime.now(clock)

        val unavailableTime =
            unavailableTimeRepository.findById(requireNotNull(unavailableTimeId))
                .orElseThrow {
                    BusinessException(
                        UnavailableTimeErrorCode.NOT_FOUND
                    )
                }

        validateOwner(userId, unavailableTime)
        validateExistingFutureTime(unavailableTime, now)
        validateTimeRange(
            startAt,
            endAt
        )
        validateFutureTime(startAt, now)
        validateUnavailableTimeOverlap(
            userId,
            unavailableTimeId,
            startAt,
            endAt
        )
        validateOfficialShiftConflict(
            userId,
            startAt,
            endAt,
            confirmOfficialShiftConflict
        )

        unavailableTime.update(
            startAt,
            endAt
        )

        return unavailableTime
    }

    fun delete(unavailableTimeId: Long?) {
        val userId = rq.actorId

        userService.getByIdForUpdate(
            userId
        )

        val unavailableTime =
            unavailableTimeRepository.findById(requireNotNull(unavailableTimeId)).orElseThrow {
                BusinessException(
                    UnavailableTimeErrorCode.NOT_FOUND
                )
            }

        validateOwner(userId, unavailableTime)

        unavailableTimeRepository.delete(unavailableTime)
    }

    //본인 일정인가
    private fun validateOwner(
        userId: Long?,
        unavailableTime: UnavailableTime
    ) {
        if (unavailableTime.user.id != userId) {
            throw BusinessException(
                UnavailableTimeErrorCode.FORBIDDEN
            )
        }
    }

    //새로운 시작 시간이 미래인지
    private fun validateFutureTime(
        startAt: LocalDateTime,
        now: LocalDateTime
    ) {
        if (!startAt.isAfter(now)) {
            throw BusinessException(
                UnavailableTimeErrorCode.NOT_FUTURE_TIME
            )
        }
    }

    //기존 일정 자체가 아직 시작 전인지
    private fun validateExistingFutureTime(
        unavailableTime: UnavailableTime,
        now: LocalDateTime
    ) {
        if (!unavailableTime.startAt
                .isAfter(now)
        ) {
            throw BusinessException(
                UnavailableTimeErrorCode.NOT_MODIFIABLE_TIME
            )
        }
    }

    //시작시간<끝나는 시간
    private fun validateTimeRange(
        startAt: LocalDateTime,
        endAt: LocalDateTime
    ) {
        if (!TimeRangeUtils.isValidRange(startAt, endAt)) {
            throw BusinessException(
                UnavailableTimeErrorCode.INVALID_TIME_RANGE
            )
        }
    }

    //본인 불가능 일정이랑 겹치는가
    private fun validateUnavailableTimeOverlap(
        userId: Long?,
        excludeUnavailableTimeId: Long?,
        startAt: LocalDateTime,
        endAt: LocalDateTime
    ) {
        val overlaps: Boolean

        if (excludeUnavailableTimeId == null) {
            overlaps =
                unavailableTimeRepository
                    .existsOverlappingUnavailableTime(
                        userId,
                        startAt,
                        endAt
                    )
        } else {
            overlaps =
                unavailableTimeRepository
                    .existsOverlappingUnavailableTimeExcludingId(
                        userId,
                        excludeUnavailableTimeId,
                        startAt,
                        endAt
                    )
        }

        if (overlaps) {
            throw BusinessException(
                UnavailableTimeErrorCode.TIME_OVERLAP
            )
        }
    }

    //shift하고 시간이 겹치는가
    private fun validateOfficialShiftConflict(
        userId: Long?,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        confirmOfficialShiftConflict: Boolean?
    ) {
        val hasConflictShift =
            shiftRepository.existsOverlappingOfficialShift(
                userId,
                ScheduleStatus.PUBLISHED,
                startAt,
                endAt,
                null
            )

        if (hasConflictShift && !java.lang.Boolean.TRUE.equals(
                confirmOfficialShiftConflict
            )
        ) {
            throw BusinessException(
                UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT,
                ConfirmationRequiredResponse(true)
            )
        }
    }
}
