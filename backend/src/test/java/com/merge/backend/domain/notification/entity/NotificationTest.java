package com.merge.backend.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void createNotification() {
        WorkplaceMember recipientMember = mock(WorkplaceMember.class);

        Notification notification = new Notification(
            recipientMember,
            NotificationType.SCHEDULE_PUBLISHED,
            "새로운 주간 근무표가 공개되었습니다."
        );

        assertThat(notification.getRecipientMember()).isSameAs(recipientMember);
        assertThat(notification.getType())
            .isEqualTo(NotificationType.SCHEDULE_PUBLISHED);
        assertThat(notification.getMessage())
            .isEqualTo("새로운 주간 근무표가 공개되었습니다.");
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    void markAsRead() {
        WorkplaceMember recipientMember = mock(WorkplaceMember.class);

        Notification notification = new Notification(
            recipientMember,
            NotificationType.SCHEDULE_PUBLISHED,
            "새로운 주간 근무표가 공개되었습니다."
        );

        LocalDateTime readAt =
            LocalDateTime.of(2026, 9, 23, 10, 0);

        notification.markAsRead(readAt);

        assertThat(notification.getReadAt()).isEqualTo(readAt);
    }

    @Test
    void markAsReadKeepsFirstReadAt() {
        WorkplaceMember recipientMember = mock(WorkplaceMember.class);

        Notification notification = new Notification(
            recipientMember,
            NotificationType.SCHEDULE_PUBLISHED,
            "새로운 주간 근무표가 공개되었습니다."
        );

        LocalDateTime firstReadAt =
            LocalDateTime.of(2026, 9, 23, 10, 0);
        LocalDateTime secondReadAt =
            LocalDateTime.of(2026, 9, 23, 10, 30);

        notification.markAsRead(firstReadAt);
        notification.markAsRead(secondReadAt);

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    void markAsReadRejectsNullBeforeAndAfterRead() {
        Notification notification = new Notification(
            mock(WorkplaceMember.class),
            NotificationType.SCHEDULE_PUBLISHED,
            "null input boundary"
        );

        assertThatThrownBy(() -> notification.markAsRead(null))
            .isInstanceOf(NullPointerException.class);
        assertThat(notification.getReadAt()).isNull();

        LocalDateTime first = LocalDateTime.of(2026, 9, 23, 10, 0);
        notification.markAsRead(first);

        assertThatThrownBy(() -> notification.markAsRead(null))
            .isInstanceOf(NullPointerException.class);
        assertThat(notification.getReadAt()).isEqualTo(first);
    }

}