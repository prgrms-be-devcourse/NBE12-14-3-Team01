package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.service.NotificationService
import com.merge.backend.domain.substitute.event.SubstituteCandidateAcceptedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SubstituteCandidateAcceptedEventListener(
    private val notificationService: NotificationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSubstituteCandidateAccepted(event: SubstituteCandidateAcceptedEvent) {
        try {
            notificationService.createSubstituteAcceptedNotifications(
                event.managerMemberIds,
            )
        } catch (e: Exception) {
            log.error(
                "대타 수락 알림 생성 실패. requestId={}, managerMemberIds={}",
                event.requestId,
                event.managerMemberIds,
                e,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SubstituteCandidateAcceptedEventListener::class.java)
    }
}
