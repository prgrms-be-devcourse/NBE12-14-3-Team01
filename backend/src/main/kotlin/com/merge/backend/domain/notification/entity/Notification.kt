package com.merge.backend.domain.notification.entity

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
class Notification(
    recipientMember: WorkplaceMember,
    type: NotificationType,
    message: String
) : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "recipient_member_id", nullable = false)
    var recipientMember: WorkplaceMember = recipientMember
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var type: NotificationType = type
        protected set

    @field:Column(nullable = false)
    var message: String = message
        protected set

    var readAt: LocalDateTime? = null
        protected set

    fun markAsRead(readAt: LocalDateTime) {
        if (this.readAt == null) {
            this.readAt = readAt
        }
    }
}
