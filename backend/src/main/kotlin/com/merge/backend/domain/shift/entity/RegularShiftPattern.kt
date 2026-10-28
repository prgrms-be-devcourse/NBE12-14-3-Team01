package com.merge.backend.domain.shift.entity

import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.DayOfWeek
import java.time.LocalTime

@Entity
class RegularShiftPattern(
    member: WorkplaceMember,
    dayOfWeek: DayOfWeek,
    startTime: LocalTime,
    endTime: LocalTime
) : BaseEntity() {
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "member_id", nullable = false)
    var member: WorkplaceMember = member
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var dayOfWeek: DayOfWeek = dayOfWeek
        protected set

    @field:Column(nullable = false)
    var startTime: LocalTime = startTime
        protected set

    @field:Column(nullable = false)
    var endTime: LocalTime = endTime
        protected set

    fun update(
        member: WorkplaceMember,
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime
    ) {
        this.member = member
        this.dayOfWeek = dayOfWeek
        this.startTime = startTime
        this.endTime = endTime
    }
}
