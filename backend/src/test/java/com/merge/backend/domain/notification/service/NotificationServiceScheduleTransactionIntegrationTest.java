package com.merge.backend.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.merge.backend.domain.notification.entity.Notification;
import com.merge.backend.domain.notification.entity.NotificationType;
import com.merge.backend.domain.notification.repository.NotificationRepository;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.domain.workplace.repository.WorkplaceRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceScheduleTransactionIntegrationTest {

    private static final String SCHEDULE_PUBLISHED_MESSAGE =
        "새로운 주간 근무표가 공개되었습니다.";

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private WorkplaceMemberRepository workplaceMemberRepository;

    @Autowired
    private WorkplaceRepository workplaceRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void scheduleNotificationPersistsInNewTransactionWhenCallerRollsBack() {
        Fixture fixture = persistFixture();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notificationService.createSchedulePublishedNotifications(
                List.of(fixture.recipientMemberId())
            );
            status.setRollbackOnly();
        });

        List<Notification> notifications = new TransactionTemplate(transactionManager)
            .execute(status -> notificationRepository.findAllByUserId(fixture.userId()));

        assertThat(notifications)
            .extracting(Notification::getRecipientMember)
            .extracting(WorkplaceMember::getId)
            .containsExactly(fixture.recipientMemberId());
        assertThat(notifications)
            .extracting(Notification::getType)
            .containsExactly(NotificationType.SCHEDULE_PUBLISHED);
        assertThat(notifications)
            .extracting(Notification::getMessage)
            .containsExactly(SCHEDULE_PUBLISHED_MESSAGE);
    }

    private Fixture persistFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            User user = new User("schedule-tx-" + suffix + "@test.com", "pw", "Schedule TX");
            entityManager.persist(user);

            Workplace workplace = new Workplace("Schedule TX", suffix);
            entityManager.persist(workplace);

            WorkplaceMember recipient = new WorkplaceMember(
                workplace,
                user,
                WorkplaceRole.EMPLOYEE,
                LocalDateTime.of(2026, 9, 1, 9, 0),
                null
            );
            entityManager.persist(recipient);
            entityManager.flush();

            return new Fixture(user.getId(), recipient.getId());
        });
    }

    private record Fixture(Long userId, Long recipientMemberId) {}
}
