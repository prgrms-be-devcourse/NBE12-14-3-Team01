package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.service.NotificationService
import com.merge.backend.domain.substitute.event.SubstituteAllCandidatesRejectedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SubstituteAllCandidatesRejectedEventListener(
    private val notificationService: NotificationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSubstituteAllCandidatesRejected(event: SubstituteAllCandidatesRejectedEvent) {
        try {
            notificationService.createSubstituteAllRejectedNotifications(
                event.recipientMemberIds,
            )
        } catch (e: Exception) {
            log.error(
                "대타 후보 전원 거절 알림 생성 실패. requestId={}, recipientMemberIds={}",
                event.requestId,
                event.recipientMemberIds,
                e,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SubstituteAllCandidatesRejectedEventListener::class.java)
    }
}
