package com.merge.backend.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.merge.backend.domain.notification.entity.Notification;
import com.merge.backend.domain.notification.entity.NotificationType;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationPersistenceIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void dirtyCheckingPersistsOnlyFirstReadAt() {
        Long id = persistFixture();
        LocalDateTime first = LocalDateTime.of(2026, 9, 23, 10, 0);
        LocalDateTime second = first.plusMinutes(30);

        Notification notification = entityManager.find(Notification.class, id);
        assertThat(notification.getCreateDate()).isNotNull();
        assertThat(notification.getModifyDate()).isNotNull();
        assertThat(notification.getReadAt()).isNull();
        notification.markAsRead(first);
        entityManager.flush();
        entityManager.clear();

        Notification reloaded = entityManager.find(Notification.class, id);
        assertThat(reloaded.getReadAt()).isEqualTo(first);
        reloaded.markAsRead(second);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Notification.class, id).getReadAt())
            .isEqualTo(first);
    }

    @Test
    void referenceIsProxyAndRecipientStaysLazyUntilUsed() {
        Long id = persistFixture();

        Notification reference = entityManager.getReference(Notification.class, id);
        assertThat(reference).isInstanceOf(HibernateProxy.class);
        assertThat(Hibernate.isInitialized(reference)).isFalse();

        assertThat(reference.getMessage()).isEqualTo("JPA migration fixture");
        assertThat(Hibernate.isInitialized(reference)).isTrue();
        WorkplaceMember recipient = reference.getRecipientMember();
        assertThat(recipient).isInstanceOf(HibernateProxy.class);
        assertThat(Hibernate.isInitialized(recipient)).isFalse();

        assertThat(recipient.getRole()).isEqualTo(WorkplaceRole.EMPLOYEE);
        assertThat(Hibernate.isInitialized(recipient)).isTrue();
    }

    @Test
    void repositoryAcquiresPessimisticWriteLock() {
        Long id = persistFixture();

        Notification locked = notificationRepository.findByIdForUpdate(id);

        assertThat(locked).isNotNull();
        assertThat(entityManager.getLockMode(locked))
            .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(locked.getId()).isEqualTo(id);
    }

    @Test
    void missingNotificationReturnsNull() {
        assertThat(notificationRepository.findByIdForUpdate(Long.MIN_VALUE)).isNull();
    }

    private Long persistFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = new User("jpa-" + suffix + "@test.com", "pw", "JPA user");
        entityManager.persist(user);
        Workplace workplace = new Workplace("JPA workplace", suffix);
        entityManager.persist(workplace);
        WorkplaceMember member = new WorkplaceMember(
            workplace, user, WorkplaceRole.EMPLOYEE,
            LocalDateTime.of(2026, 9, 1, 9, 0), null
        );
        entityManager.persist(member);
        Notification notification = new Notification(
            member, NotificationType.SCHEDULE_PUBLISHED, "JPA migration fixture"
        );
        entityManager.persist(notification);
        entityManager.flush();
        Long id = notification.getId();
        assertThat(id).isNotNull();
        entityManager.clear();
        return id;
    }
}
