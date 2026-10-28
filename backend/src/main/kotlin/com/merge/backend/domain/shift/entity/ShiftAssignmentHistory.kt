package com.merge.backend.domain.shift.entity

import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "shift_assignment_history",
    indexes = [
        Index(name = "idx_shift_assignment_history_shift_id", columnList = "shift_id"),
        Index(
            name = "idx_shift_assignment_history_previous_member_created_date",
            columnList = "previous_member_id, create_date"
        ),
    ],
)
class ShiftAssignmentHistory(
    shift: Shift,
    previousMember: WorkplaceMember,
    newMember: WorkplaceMember,
    reason: ShiftAssignmentReason,
    actorMember: WorkplaceMember?,
) : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "shift_id", nullable = false)
    var shift: Shift = shift
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "previous_member_id", nullable = false)
    var previousMember: WorkplaceMember = previousMember
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "new_member_id", nullable = false)
    var newMember: WorkplaceMember = newMember
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var reason: ShiftAssignmentReason = reason
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "actor_member_id")
    var actorMember: WorkplaceMember? = actorMember
        protected set
}
