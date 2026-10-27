package com.merge.backend.domain.notification.entity

import com.merge.backend.domain.workplace.entity.WorkplaceMember
import java.lang.reflect.InvocationTargetException
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class NotificationTest {
    @Test
    fun createNotification() {
        val recipientMember = mock<WorkplaceMember>()
        val notification = Notification(
            recipientMember,
            NotificationType.SCHEDULE_PUBLISHED,
            "새로운 주간 근무표가 공개되었습니다.",
        )

        assertThat(notification.recipientMember).isSameAs(recipientMember)
        assertThat(notification.type).isEqualTo(NotificationType.SCHEDULE_PUBLISHED)
        assertThat(notification.message).isEqualTo("새로운 주간 근무표가 공개되었습니다.")
        assertThat(notification.readAt).isNull()
    }

    @Test
    fun markAsRead() {
        val recipientMember = mock<WorkplaceMember>()
        val notification = Notification(
            recipientMember,
            NotificationType.SCHEDULE_PUBLISHED,
            "새로운 주간 근무표가 공개되었습니다.",
        )
        val readAt = LocalDateTime.of(2026, 9, 23, 10, 0)

        notification.markAsRead(readAt)

        assertThat(notification.readAt).isEqualTo(readAt)
    }

    @Test
    fun markAsReadKeepsFirstReadAt() {
        val recipientMember = mock<WorkplaceMember>()
        val notification = Notification(
            recipientMember,
            NotificationType.SCHEDULE_PUBLISHED,
            "새로운 주간 근무표가 공개되었습니다.",
        )
        val firstReadAt = LocalDateTime.of(2026, 9, 23, 10, 0)
        val secondReadAt = LocalDateTime.of(2026, 9, 23, 10, 30)

        notification.markAsRead(firstReadAt)
        notification.markAsRead(secondReadAt)

        assertThat(notification.readAt).isEqualTo(firstReadAt)
    }

    @Test
    fun markAsReadRejectsNullBeforeAndAfterRead() {
        val notification = Notification(
            mock<WorkplaceMember>(),
            NotificationType.SCHEDULE_PUBLISHED,
            "null input boundary",
        )
        val javaMethod = Notification::class.java.getMethod("markAsRead", LocalDateTime::class.java)

        // Java callers can still pass null. Reflection invokes that JVM boundary directly.
        assertThatThrownBy { javaMethod.invoke(notification, null) }
            .isInstanceOf(InvocationTargetException::class.java)
            .hasCauseInstanceOf(NullPointerException::class.java)
        assertThat(notification.readAt).isNull()

        val first = LocalDateTime.of(2026, 9, 23, 10, 0)
        notification.markAsRead(first)

        assertThatThrownBy { javaMethod.invoke(notification, null) }
            .isInstanceOf(InvocationTargetException::class.java)
            .hasCauseInstanceOf(NullPointerException::class.java)
        assertThat(notification.readAt).isEqualTo(first)
    }
}
