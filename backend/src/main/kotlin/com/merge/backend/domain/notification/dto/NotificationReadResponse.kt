package com.merge.backend.domain.notification.dto

import com.merge.backend.domain.notification.entity.Notification
import java.time.LocalDateTime

data class NotificationReadResponse(
    val notificationId: Long?,
    val readAt: LocalDateTime?
) {
    companion object {
        @JvmStatic
        fun from(notification: Notification): NotificationReadResponse =
            NotificationReadResponse(
                notification.id,
                notification.readAt
            )
    }
}
