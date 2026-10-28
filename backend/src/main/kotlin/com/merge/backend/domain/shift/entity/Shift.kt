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
import java.time.LocalDateTime

@Entity
class Shift(
    schedule: Schedule,
    member: WorkplaceMember,
    startAt: LocalDateTime,
    endAt: LocalDateTime,
    status: ShiftStatus
) : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "schedule_id", nullable = false)
    var schedule: Schedule = schedule
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "member_id", nullable = false)
    var member: WorkplaceMember = member
        protected set

    @field:Column(nullable = false)
    var startAt: LocalDateTime = startAt
        protected set

    @field:Column(nullable = false)
    var endAt: LocalDateTime = endAt
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var status: ShiftStatus = status
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "cancellation_reason")
    var cancellationReason: ShiftCancellationReason? = null
        protected set

    @field:Column(name = "cancelled_at")
    var cancelledAt: LocalDateTime? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "cancelled_by_member_id")
    var cancelledBy: WorkplaceMember? = null
        protected set

    fun update(
        member: WorkplaceMember,
        startAt: LocalDateTime,
        endAt: LocalDateTime
    ): Shift {
        this.member = member
        this.startAt = startAt
        this.endAt = endAt

        return this
    }

    fun changeMember(member: WorkplaceMember) {
        this.member = member
    }

    fun cancel(
        reason: ShiftCancellationReason,
        cancelledAt: LocalDateTime,
        cancelledBy: WorkplaceMember?
    ) {
        status = ShiftStatus.CANCELLED
        cancellationReason = reason
        this.cancelledAt = cancelledAt
        this.cancelledBy = cancelledBy
    }
}
