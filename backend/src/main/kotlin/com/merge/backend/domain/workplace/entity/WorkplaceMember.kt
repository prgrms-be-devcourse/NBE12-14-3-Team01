package com.merge.backend.domain.workplace.entity

import com.merge.backend.domain.user.entity.User
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
import java.time.LocalDateTime

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["workplace_id", "user_id"])
    ]
)
class WorkplaceMember() : BaseEntity() {
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "workplace_id", nullable = false)
    var workplace: Workplace? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "user_id", nullable = false)
    var user: User? = null
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var role: WorkplaceRole? = null
        protected set

    @field:Column(nullable = false)
    var joinedAt: LocalDateTime? = null
        protected set

    var leftAt: LocalDateTime? = null
        protected set

    constructor(
        workplace: Workplace?,
        user: User?,
        role: WorkplaceRole?,
        joinedAt: LocalDateTime?
    ) : this(workplace, user, role, joinedAt, null)

    constructor(
        workplace: Workplace?,
        user: User?,
        role: WorkplaceRole?,
        joinedAt: LocalDateTime?,
        leftAt: LocalDateTime?
    ) : this() {
        this.workplace = workplace
        this.user = user
        this.role = role
        this.joinedAt = joinedAt
        this.leftAt = leftAt
    }
}
