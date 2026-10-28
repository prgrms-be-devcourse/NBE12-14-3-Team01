package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.service.NotificationService
import com.merge.backend.domain.shift.event.SchedulePublishedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SchedulePublishedEventListener(
    private val notificationService: NotificationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSchedulePublished(event: SchedulePublishedEvent) {
        try {
            notificationService.createSchedulePublishedNotifications(
                event.recipientMemberIds(),
            )
        } catch (e: Exception) {
            log.error(
                "Schedule 공개 알림 생성 실패. scheduleId={}, recipientMemberIds={}",
                event.scheduleId(),
                event.recipientMemberIds(),
                e,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SchedulePublishedEventListener::class.java)
    }
}
