package com.merge.backend.domain.shift.repository

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import jakarta.persistence.LockModeType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ShiftRepository : JpaRepository<Shift, Long> {

    fun findBySchedule_IdOrderByStartAtAscIdAsc(
        scheduleId: Long?
    ): List<Shift>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT s
        FROM Shift s
        WHERE s.id = :shiftId
        """
    )
    fun findByIdForUpdate(
        @Param("shiftId") shiftId: Long?
    ): Optional<Shift>

    //동일 Schedule 내 동일 WorkplaceMember의 다른 Shift 중복 검증
    @Query(
        """
        SELECT COUNT(s) > 0 
        FROM Shift s 
        WHERE s.schedule.id = :scheduleId 
          AND s.member.id = :memberId 
          AND s.startAt < :endAt 
          AND s.endAt > :startAt
          AND (:currentShiftId IS NULL OR s.id != :currentShiftId)
          AND s.status = ShiftStatus.SCHEDULED
        """
    )
    fun existsOverlappingInSchedule(
        @Param("scheduleId") scheduleId: Long?,
        @Param("memberId") memberId: Long?,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
        @Param("currentShiftId") currentShiftId: Long?
    ): Boolean

    //해당 User의 모든 Workplace "공식/확정 Shift" 중복 검증 (투잡/타매장 포함)
    @Query(
        """
        SELECT COUNT(s) > 0 
        FROM Shift s 
        WHERE s.member.user.id = :userId 
          AND s.schedule.status = :scheduleStatus
          AND s.startAt < :endAt 
          AND s.endAt > :startAt
          AND (:currentShiftId IS NULL OR s.id != :currentShiftId)
          AND s.status = ShiftStatus.SCHEDULED
        """
    )
    fun existsOverlappingOfficialShift(
        @Param("userId") userId: Long?,
        @Param("scheduleStatus") scheduleStatus: ScheduleStatus,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
        @Param("currentShiftId") currentShiftId: Long?
    ): Boolean

    //해당 주차의 모든 스케줄에서 본인 근무 조회
    @Query(
        """
        SELECT s
        FROM Shift s 
        WHERE s.member.user.id = :currentUserId 
          AND s.member.leftAt IS NULL
          AND s.schedule.status = ScheduleStatus.PUBLISHED
          AND s.status = ShiftStatus.SCHEDULED
          AND s.schedule.weekStartDate = :weekStartDate
        ORDER BY s.startAt ASC
        """
    )
    fun findAllByWeekStartDateAndCurrentUserId(
        @Param("weekStartDate") weekStartDate: LocalDate,
        @Param("currentUserId") currentUserId: Long?
    ): List<Shift>

    //수락 이후 승인 이전 사이 혹시나 새로운 근무가 겹치지 않는지
    @Query(
        """
        select count(s) > 0 
        from Shift s 
        where s.member.user.id = :userId
        and s.schedule.status = ScheduleStatus.PUBLISHED
        and s.status = ShiftStatus.SCHEDULED
        and s.startAt < :endAt 
        and s.endAt > :startAt
        """
    )
    fun existsConflictingShift(
        @Param("userId") userId: Long?,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime
    ): Boolean

    // API-SC-01 - 나의 현재 소속이 담당이던 근무 중 공개 후 취소된 것 (원래 시작 시각이 :from 이후)
    @Query(
        """
        SELECT s
        FROM Shift s
        JOIN FETCH s.schedule sc
        JOIN FETCH sc.workplace
        WHERE s.member.user.id = :userId
          AND s.member.leftAt IS NULL
          AND s.status = ShiftStatus.CANCELLED
          AND s.cancelledAt IS NOT NULL
          AND s.startAt >= :from
        """
    )
    fun findCancelledShiftsOfCurrentMembers(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDateTime
    ): List<Shift>
}
