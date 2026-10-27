package com.merge.backend.domain.shift.repository

import com.merge.backend.domain.shift.entity.Schedule
import jakarta.persistence.LockModeType
import java.time.LocalDate
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ScheduleRepository : JpaRepository<Schedule, Long> {

    fun findByWorkplace_IdAndWeekStartDate(
        workplaceId: Long?,
        weekStartDate: LocalDate?
    ): Optional<Schedule>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT s
        FROM Schedule s
        WHERE s.workplace.id = :workplaceId
          AND s.weekStartDate = :weekStartDate
        """
    )
    fun findByWorkplaceAndWeekStartDateForUpdate(
        @Param("workplaceId") workplaceId: Long?,
        @Param("weekStartDate") weekStartDate: LocalDate?
    ): Optional<Schedule>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT s
        FROM Schedule s
        WHERE s.id = :scheduleId
        """
    )
    fun findByIdForUpdate(
        @Param("scheduleId") scheduleId: Long?
    ): Optional<Schedule>
}
