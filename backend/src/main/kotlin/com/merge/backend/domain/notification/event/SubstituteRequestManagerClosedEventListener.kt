package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.service.NotificationService
import com.merge.backend.domain.substitute.event.SubstituteRequestManagerClosedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SubstituteRequestManagerClosedEventListener(
    private val notificationService: NotificationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSubstituteRequestManagerClosed(event: SubstituteRequestManagerClosedEvent) {
        try {
            notificationService.createSubstituteManagerClosedNotifications(
                event.recipientMemberIds,
            )
        } catch (e: Exception) {
            log.error(
                "대체 근무 관리자 종료 알림 생성 실패. requestId={}, recipientMemberIds={}",
                event.requestId,
                event.recipientMemberIds,
                e,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SubstituteRequestManagerClosedEventListener::class.java)
    }
}
