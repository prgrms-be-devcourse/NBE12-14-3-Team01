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
class WorkplaceMember @JvmOverloads constructor(
    workplace: Workplace,
    user: User,
    role: WorkplaceRole,
    joinedAt: LocalDateTime,
    leftAt: LocalDateTime? = null
) : BaseEntity() {
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "workplace_id", nullable = false)
    var workplace: Workplace = workplace
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "user_id", nullable = false)
    var user: User = user
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var role: WorkplaceRole = role
        protected set

    @field:Column(nullable = false)
    var joinedAt: LocalDateTime = joinedAt
        protected set

    var leftAt: LocalDateTime? = leftAt
        protected set
}
