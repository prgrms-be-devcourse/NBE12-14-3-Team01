package com.merge.backend.domain.notification.repository

import com.merge.backend.domain.notification.entity.Notification
import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationPersistenceIntegrationTest {
    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun dirtyCheckingPersistsOnlyFirstReadAt() {
        val id = persistFixture()
        val first = LocalDateTime.of(2026, 9, 23, 10, 0)
        val second = first.plusMinutes(30)
        val notification = entityManager.find(Notification::class.java, id)
        assertThat(notification.createDate).isNotNull()
        assertThat(notification.modifyDate).isNotNull()
        assertThat(notification.readAt).isNull()
        notification.markAsRead(first)
        entityManager.flush()
        entityManager.clear()

        val reloaded = entityManager.find(Notification::class.java, id)
        assertThat(reloaded.readAt).isEqualTo(first)
        reloaded.markAsRead(second)
        entityManager.flush()
        entityManager.clear()
        assertThat(entityManager.find(Notification::class.java, id).readAt).isEqualTo(first)
    }

    @Test
    fun referenceIsProxyAndRecipientStaysLazyUntilUsed() {
        val id = persistFixture()
        val reference = entityManager.getReference(Notification::class.java, id)
        assertThat(reference).isInstanceOf(HibernateProxy::class.java)
        assertThat(Hibernate.isInitialized(reference)).isFalse()
        assertThat(reference.message).isEqualTo("JPA migration fixture")
        assertThat(Hibernate.isInitialized(reference)).isTrue()
        val recipient = reference.recipientMember
        assertThat(recipient).isInstanceOf(HibernateProxy::class.java)
        assertThat(Hibernate.isInitialized(recipient)).isFalse()
        assertThat(recipient.role).isEqualTo(WorkplaceRole.EMPLOYEE)
        assertThat(Hibernate.isInitialized(recipient)).isTrue()
    }

    @Test
    fun repositoryAcquiresPessimisticWriteLock() {
        val id = persistFixture()
        val locked = notificationRepository.findByIdForUpdate(id)
        assertThat(locked).isNotNull()
        val present = requireNotNull(locked)
        assertThat(entityManager.getLockMode(present)).isEqualTo(LockModeType.PESSIMISTIC_WRITE)
        assertThat(present.id).isEqualTo(id)
    }

    @Test
    fun missingNotificationReturnsNull() {
        assertThat(notificationRepository.findByIdForUpdate(Long.MIN_VALUE)).isNull()
    }

    private fun persistFixture(): Long {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val user = User("jpa-${suffix}@test.com", "pw", "JPA user")
        entityManager.persist(user)
        val workplace = Workplace("JPA workplace", suffix)
        entityManager.persist(workplace)
        val member = WorkplaceMember(
            workplace, user, WorkplaceRole.EMPLOYEE,
            LocalDateTime.of(2026, 9, 1, 9, 0), null,
        )
        entityManager.persist(member)
        val notification = Notification(member, NotificationType.SCHEDULE_PUBLISHED, "JPA migration fixture")
        entityManager.persist(notification)
        entityManager.flush()
        val id = notification.id
        assertThat(id).isNotNull()
        entityManager.clear()
        return requireNotNull(id)
    }
}
