package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.service.NotificationService
import com.merge.backend.domain.substitute.event.SubstituteRequestApprovedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SubstituteRequestApprovedEventListener(
    private val notificationService: NotificationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSubstituteRequestApproved(event: SubstituteRequestApprovedEvent) {
        try {
            notificationService.createSubstituteApprovedNotifications(
                event.requesterMemberId,
                event.acceptedMemberId,
            )
        } catch (e: Exception) {
            log.error(
                "대체 근무 최종 승인 알림 생성 실패. requestId={}, requesterMemberId={}, acceptedMemberId={}",
                event.requestId,
                event.requesterMemberId,
                event.acceptedMemberId,
                e,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SubstituteRequestApprovedEventListener::class.java)
    }
}
