package com.merge.backend.domain.notification.dto

import com.merge.backend.domain.notification.entity.Notification
import com.merge.backend.domain.notification.entity.NotificationType
import java.time.LocalDateTime

data class NotificationResponse(
    val notificationId: Long?,
    val workplaceId: Long?,
    val workplaceName: String?,
    val type: NotificationType?,
    val message: String?,
    val createdAt: LocalDateTime?,
    val readAt: LocalDateTime?
) {
    companion object {
        @JvmStatic
        fun from(notification: Notification): NotificationResponse =
            NotificationResponse(
                notification.id,
                notification.recipientMember.workplace.id,
                notification.recipientMember.workplace.name,
                notification.type,
                notification.message,
                notification.createDate,
                notification.readAt
            )
    }
}
