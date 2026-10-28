package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.service.NotificationService
import com.merge.backend.domain.shift.event.SchedulePublishedEvent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchedulePublishedEventListenerIntegrationTest {
    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockitoBean
    private lateinit var notificationService: NotificationService

    @Test
    @DisplayName("SchedulePublishedEvent는 원 Transaction commit 후 처리된다")
    fun handlesSchedulePublishedEventAfterCommit() {
        val recipientMemberIds = listOf(100L, 200L)
        val event = SchedulePublishedEvent(20L, recipientMemberIds)

        applicationEventPublisher.publishEvent(event)
        verifyNoInteractions(notificationService)

        TestTransaction.flagForCommit()
        TestTransaction.end()

        verify(notificationService).createSchedulePublishedNotifications(recipientMemberIds)
    }
}
