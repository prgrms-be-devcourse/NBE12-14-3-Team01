package com.merge.backend.domain.notification.service

import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.notification.repository.NotificationRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceScheduleTransactionIntegrationTest {
    @Autowired
    private lateinit var notificationService: NotificationService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var workplaceMemberRepository: com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository

    @Autowired
    private lateinit var workplaceRepository: com.merge.backend.domain.workplace.repository.WorkplaceRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun scheduleNotificationPersistsInNewTransactionWhenCallerRollsBack() {
        val fixture = persistFixture()
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            notificationService.createSchedulePublishedNotifications(listOf(fixture.recipientMemberId))
            status.setRollbackOnly()
        }
        val notifications = TransactionTemplate(transactionManager).execute {
            notificationRepository.findAllByUserId(fixture.userId)
        }
        assertThat(notifications.map { it.recipientMember.id })
            .containsExactly(fixture.recipientMemberId)
        assertThat(notifications.map { it.type })
            .containsExactly(NotificationType.SCHEDULE_PUBLISHED)
        assertThat(notifications.map { it.message })
            .containsExactly(SCHEDULE_PUBLISHED_MESSAGE)
    }

    private fun persistFixture(): Fixture =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            val suffix = UUID.randomUUID().toString().replace("-", "")
            val user = User("schedule-tx-${suffix}@test.com", "pw", "Schedule TX")
            entityManager.persist(user)
            val workplace = Workplace("Schedule TX", suffix)
            entityManager.persist(workplace)
            val recipient = WorkplaceMember(
                workplace,
                user,
                WorkplaceRole.EMPLOYEE,
                LocalDateTime.of(2026, 9, 1, 9, 0),
                null,
            )
            entityManager.persist(recipient)
            entityManager.flush()
            Fixture(checkNotNull(user.id), checkNotNull(recipient.id))
        })

    private data class Fixture(val userId: Long, val recipientMemberId: Long)

    private companion object {
        private const val SCHEDULE_PUBLISHED_MESSAGE = "새로운 주간 근무표가 공개되었습니다."
    }
}
