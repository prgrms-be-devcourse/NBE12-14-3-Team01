package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// Commit 후 받는 사람마다 따로 저장한다. 한 사람의 실패가 다른 사람의 알림을 막지 않는다 (NOTIFICATION-TYPES §3-2 R5)
@Component
class PlannedNotificationsEventListener(
    private val notificationService: NotificationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePlannedNotifications(event: PlannedNotificationsEvent) {
        event.notifications.forEach { notification ->
            try {
                notificationService.createPlannedNotification(notification)
            } catch (e: Exception) {
                log.error("알림 생성 실패. source={}, notification={}", event.source, notification, e)
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(PlannedNotificationsEventListener::class.java)
    }
}
