package com.merge.backend.domain.shift.entity

import com.merge.backend.domain.user.entity.User
import com.merge.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

@Entity
class UnavailableTime(
    user: User,
    startAt: LocalDateTime,
    endAt: LocalDateTime
) : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "user_id", nullable = false)
    var user: User = user
        protected set

    @field:Column(nullable = false)
    var startAt: LocalDateTime = startAt
        protected set

    @field:Column(nullable = false)
    var endAt: LocalDateTime = endAt
        protected set

    fun update(
        startAt: LocalDateTime,
        endAt: LocalDateTime
    ) {
        this.startAt = startAt
        this.endAt = endAt
    }
}
