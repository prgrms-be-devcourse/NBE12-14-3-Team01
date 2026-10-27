package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.service.NotificationService
import com.merge.backend.domain.substitute.event.SubstituteNoCandidateEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SubstituteNoCandidateEventListener(
    private val notificationService: NotificationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSubstituteNoCandidate(event: SubstituteNoCandidateEvent) {
        try {
            notificationService.createSubstituteNoCandidateNotifications(
                event.managerMemberIds,
            )
        } catch (e: Exception) {
            log.error(
                "대타 후보 없음 알림 생성 실패. shiftId={}, managerMemberIds={}",
                event.shiftId,
                event.managerMemberIds,
                e,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SubstituteNoCandidateEventListener::class.java)
    }
}
