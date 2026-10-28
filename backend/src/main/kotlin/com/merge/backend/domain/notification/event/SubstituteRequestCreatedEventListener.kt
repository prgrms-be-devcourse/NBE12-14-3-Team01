package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.service.NotificationService
import com.merge.backend.domain.substitute.event.SubstituteRequestCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SubstituteRequestCreatedEventListener(
    private val notificationService: NotificationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSubstituteRequestCreated(event: SubstituteRequestCreatedEvent) {
        try {
            notificationService.createSubstituteRequestReceivedNotifications(
                event.candidateMemberIds,
            )
        } catch (e: Exception) {
            log.error(
                "대타 요청 도착 알림 생성 실패. requestId={}, candidateMemberIds={}",
                event.requestId,
                event.candidateMemberIds,
                e,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SubstituteRequestCreatedEventListener::class.java)
    }
}
