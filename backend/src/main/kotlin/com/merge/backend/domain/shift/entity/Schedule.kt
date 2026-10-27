package com.merge.backend.domain.shift.entity

import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["workplace_id", "week_start_date"])
    ]
)
class Schedule(
    workplace: Workplace,
    weekStartDate: LocalDate
) : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "workplace_id", nullable = false)
    var workplace: Workplace = workplace
        protected set

    @field:Column(name = "week_start_date", nullable = false)
    var weekStartDate: LocalDate = weekStartDate
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var status: ScheduleStatus = ScheduleStatus.DRAFT
        protected set

    var publishedAt: LocalDateTime? = null
        protected set

    fun publish(publishedAt: LocalDateTime) {
        status = ScheduleStatus.PUBLISHED
        this.publishedAt = publishedAt
    }

    fun resolveDate(dayOfWeek: DayOfWeek): LocalDate {
        val daysToAdd =
            dayOfWeek.value - DayOfWeek.MONDAY.value

        return weekStartDate.plusDays(daysToAdd.toLong())
    }
}
