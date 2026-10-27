package com.merge.backend.domain.shift.repository

import com.merge.backend.domain.shift.entity.UnavailableTime
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UnavailableTimeRepository : JpaRepository<UnavailableTime, Long> {

    //등록한 불가능 시간과 중복되는가?
    @Query(
        """
        SELECT COUNT(u) > 0 
        FROM UnavailableTime u 
        WHERE u.user.id = :userId 
          AND u.startAt < :endAt 
          AND u.endAt > :startAt
        """
    )
    fun existsOverlappingUnavailableTime(
        @Param("userId") userId: Long?,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime
    ): Boolean

    @Query(
        """
        SELECT COUNT(u) > 0
        FROM UnavailableTime u
        WHERE u.user.id = :userId
          AND u.id <> :excludeUnavailableTimeId
          AND u.startAt < :endAt
          AND u.endAt > :startAt
        """
    )
    fun existsOverlappingUnavailableTimeExcludingId(
        @Param("userId") userId: Long?,
        @Param("excludeUnavailableTimeId") excludeUnavailableTimeId: Long?,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime
    ): Boolean

    @Query(
        """
        SELECT u.id
        FROM UnavailableTime u
        WHERE u.user.id = :userId
          AND u.endAt > :now
          AND EXISTS (
              SELECT s.id
              FROM Shift s
              WHERE s.member.user.id = :userId
                AND s.schedule.status = ScheduleStatus.PUBLISHED
                AND s.status = ShiftStatus.SCHEDULED
                AND s.startAt < u.endAt
                AND s.endAt > u.startAt
          )
        """
    )
    fun findIdsWithOfficialShiftConflict(
        @Param("userId") userId: Long?,
        @Param("now") now: LocalDateTime
    ): List<Long>

    fun findByUserId(userId: Long?): List<UnavailableTime>

    fun findByUserIdAndEndAtAfterOrderByStartAtAsc(
        userId: Long?,
        now: LocalDateTime
    ): List<UnavailableTime>
}
