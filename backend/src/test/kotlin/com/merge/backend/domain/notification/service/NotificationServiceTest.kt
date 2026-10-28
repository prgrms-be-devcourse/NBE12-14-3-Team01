package com.merge.backend.domain.notification.service

import com.merge.backend.domain.notification.entity.Notification
import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.notification.exception.NotificationErrorCode
import com.merge.backend.domain.notification.repository.NotificationRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.exception.BusinessException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NotificationServiceTest {
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository
    private lateinit var notificationService: NotificationService
    private lateinit var testClock: TestClock

    @BeforeEach
    fun setUp() {
        notificationRepository = mock()
        workplaceMemberRepository = mock()
        testClock = TestClock(Instant.parse("2026-09-15T01:00:00Z"), ZoneId.of("Asia/Seoul"))
        notificationService = NotificationService(notificationRepository, workplaceMemberRepository, testClock)
    }

    @Test
    @DisplayName("알림을 생성한다")
    fun createNotification() {
        val recipientMember = mock<WorkplaceMember>()
        stubSaveReturningArgument()
        val notification = notificationService.create(
            recipientMember, NotificationType.SCHEDULE_PUBLISHED, "새로운 주간 근무표가 공개되었습니다."
        )
        assertThat(notification.recipientMember).isSameAs(recipientMember)
        assertThat(notification.type).isEqualTo(NotificationType.SCHEDULE_PUBLISHED)
        assertThat(notification.message).isEqualTo("새로운 주간 근무표가 공개되었습니다.")
        assertThat(notification.readAt).isNull()
        verify(notificationRepository).save(any<Notification>())
    }

    @Test
    @DisplayName("알림을 읽음 처리한다")
    fun markAsRead() {
        val actorUserId = 1L
        val notificationId = 100L
        val recipientUser = mock<User>()
        val recipientMember = mock<WorkplaceMember>()
        whenever(recipientMember.user).thenReturn(recipientUser)
        whenever(recipientUser.id).thenReturn(actorUserId)
        val notification = Notification(
            recipientMember, NotificationType.SCHEDULE_PUBLISHED, "새로운 주간 근무표가 공개되었습니다."
        )
        whenever(notificationRepository.findByIdForUpdate(notificationId)).thenReturn(notification)
        val result = notificationService.markAsRead(actorUserId, notificationId)
        val expectedReadAt = LocalDateTime.of(2026, 9, 15, 10, 0)
        assertThat(notification.readAt).isEqualTo(expectedReadAt)
        assertThat(result.readAt).isEqualTo(expectedReadAt)
        verify(notificationRepository).findByIdForUpdate(notificationId)
    }

    @Test
    @DisplayName("존재하지 않는 알림은 읽음 처리할 수 없다")
    fun markAsReadFailsWhenNotificationNotFound() {
        val actorUserId = 1L
        val notificationId = 100L
        whenever(notificationRepository.findByIdForUpdate(notificationId)).thenReturn(null)
        val thrown = catchThrowable { notificationService.markAsRead(actorUserId, notificationId) }
        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        assertThat((thrown as BusinessException).errorCode)
            .isEqualTo(NotificationErrorCode.NOT_FOUND_NOTIFICATION)
    }

    @Test
    @DisplayName("다른 사용자의 알림은 읽음 처리할 수 없다")
    fun markAsReadFailsWhenNotificationBelongsToAnotherUser() {
        val actorUserId = 1L
        val notificationId = 100L
        val recipientUser = mock<User>()
        val recipientMember = mock<WorkplaceMember>()
        whenever(recipientMember.user).thenReturn(recipientUser)
        whenever(recipientUser.id).thenReturn(2L)
        val notification = Notification(
            recipientMember, NotificationType.SCHEDULE_PUBLISHED, "새로운 주간 근무표가 공개되었습니다."
        )
        whenever(notificationRepository.findByIdForUpdate(notificationId)).thenReturn(notification)
        val thrown = catchThrowable { notificationService.markAsRead(actorUserId, notificationId) }
        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        assertThat((thrown as BusinessException).errorCode)
            .isEqualTo(NotificationErrorCode.FORBIDDEN_ACCESS)
        assertThat(notification.readAt).isNull()
    }

    @Test
    @DisplayName("퇴사한 근무지의 알림은 읽음 처리할 수 없다")
    fun markAsReadFailsWhenRecipientMembershipIsInactive() {
        val actorUserId = 1L
        val notificationId = 100L
        val recipientUser = mock<User>()
        val recipientMember = mock<WorkplaceMember>()
        whenever(recipientMember.user).thenReturn(recipientUser)
        whenever(recipientUser.id).thenReturn(actorUserId)
        whenever(recipientMember.leftAt).thenReturn(LocalDateTime.of(2026, 9, 30, 10, 0))
        val notification = Notification(
            recipientMember, NotificationType.SCHEDULE_PUBLISHED, "새로운 주간 근무표가 공개되었습니다."
        )
        whenever(notificationRepository.findByIdForUpdate(notificationId)).thenReturn(notification)
        val thrown = catchThrowable { notificationService.markAsRead(actorUserId, notificationId) }
        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        assertThat((thrown as BusinessException).errorCode)
            .isEqualTo(NotificationErrorCode.FORBIDDEN_ACCESS)
        assertThat(notification.readAt).isNull()
    }

    @Test
    @DisplayName("내 알림 목록을 조회한다")
    fun getNotifications() {
        val actorUserId = 1L
        val notification = mock<Notification>()
        val recipientMember = mock<WorkplaceMember>()
        val workplace = mock<Workplace>()
        val createdAt = LocalDateTime.of(2026, 9, 15, 10, 0)
        whenever(notification.id).thenReturn(100L)
        whenever(notification.recipientMember).thenReturn(recipientMember)
        whenever(recipientMember.workplace).thenReturn(workplace)
        whenever(workplace.id).thenReturn(10L)
        whenever(workplace.name).thenReturn("SWITCH 카페")
        whenever(notification.type).thenReturn(NotificationType.SCHEDULE_PUBLISHED)
        whenever(notification.message).thenReturn("새로운 주간 근무표가 공개되었습니다.")
        whenever(notification.createDate).thenReturn(createdAt)
        whenever(notification.readAt).thenReturn(null)
        whenever(notificationRepository.findAllByUserId(actorUserId)).thenReturn(listOf(notification))
        val result = notificationService.getNotifications(actorUserId)
        assertThat(result).hasSize(1)
        val response = result[0]
        assertThat(response.notificationId).isEqualTo(100L)
        assertThat(response.workplaceId).isEqualTo(10L)
        assertThat(response.workplaceName).isEqualTo("SWITCH 카페")
        assertThat(response.type).isEqualTo(NotificationType.SCHEDULE_PUBLISHED)
        assertThat(response.message).isEqualTo("새로운 주간 근무표가 공개되었습니다.")
        assertThat(response.createdAt).isEqualTo(createdAt)
        assertThat(response.readAt).isNull()
        verify(notificationRepository).findAllByUserId(actorUserId)
    }

    @Test
    @DisplayName("읽은 알림과 읽지 않은 알림을 모두 조회한다")
    fun getNotificationsIncludesReadAndUnreadNotifications() {
        val actorUserId = 1L
        val unreadNotification = mock<Notification>()
        val readNotification = mock<Notification>()
        val unreadRecipientMember = mock<WorkplaceMember>()
        val readRecipientMember = mock<WorkplaceMember>()
        val workplace = mock<Workplace>()
        val unreadCreatedAt = LocalDateTime.of(2026, 9, 15, 11, 0)
        val readCreatedAt = LocalDateTime.of(2026, 9, 15, 10, 0)
        val readAt = LocalDateTime.of(2026, 9, 15, 10, 30)
        whenever(unreadNotification.id).thenReturn(101L)
        whenever(unreadNotification.recipientMember).thenReturn(unreadRecipientMember)
        whenever(unreadRecipientMember.workplace).thenReturn(workplace)
        whenever(unreadNotification.type).thenReturn(NotificationType.SCHEDULE_PUBLISHED)
        whenever(unreadNotification.message).thenReturn("새로운 주간 근무표가 공개되었습니다.")
        whenever(unreadNotification.createDate).thenReturn(unreadCreatedAt)
        whenever(unreadNotification.readAt).thenReturn(null)
        whenever(readNotification.id).thenReturn(100L)
        whenever(readNotification.recipientMember).thenReturn(readRecipientMember)
        whenever(readRecipientMember.workplace).thenReturn(workplace)
        whenever(readNotification.type).thenReturn(NotificationType.SUBSTITUTE_APPROVED)
        whenever(readNotification.message).thenReturn("대타 요청이 최종 승인되었습니다.")
        whenever(readNotification.createDate).thenReturn(readCreatedAt)
        whenever(readNotification.readAt).thenReturn(readAt)
        whenever(workplace.id).thenReturn(10L)
        whenever(workplace.name).thenReturn("SWITCH 카페")
        whenever(notificationRepository.findAllByUserId(actorUserId))
            .thenReturn(listOf(unreadNotification, readNotification))
        val result = notificationService.getNotifications(actorUserId)
        assertThat(result).hasSize(2)
        assertThat(result[0].notificationId).isEqualTo(101L)
        assertThat(result[0].readAt).isNull()
        assertThat(result[1].notificationId).isEqualTo(100L)
        assertThat(result[1].readAt).isEqualTo(readAt)
    }

    @Test
    fun createSchedulePublishedNotifications() {
        val first = mock<WorkplaceMember>()
        val second = mock<WorkplaceMember>()
        val ids = listOf(100L, 200L)
        arrangeRecipients(ids, listOf(first, second))
        notificationService.createSchedulePublishedNotifications(ids)
        assertSavedRecipients(ids, first, second, NotificationType.SCHEDULE_PUBLISHED,
            "새로운 주간 근무표가 공개되었습니다.")
    }

    @Test
    fun createSubstituteRequestReceivedNotifications() {
        val first = mock<WorkplaceMember>()
        val second = mock<WorkplaceMember>()
        val ids = listOf(100L, 200L)
        arrangeRecipients(ids, listOf(first, second))
        notificationService.createSubstituteRequestReceivedNotifications(ids)
        assertSavedRecipients(ids, first, second, NotificationType.SUBSTITUTE_REQUEST_RECEIVED,
            "새로운 대타 요청이 도착했습니다.")
    }

    @Test
    fun createSubstituteNoCandidateNotifications() {
        val first = mock<WorkplaceMember>()
        val second = mock<WorkplaceMember>()
        val ids = listOf(100L, 200L)
        arrangeRecipients(ids, listOf(first, second))
        notificationService.createSubstituteNoCandidateNotifications(ids)
        assertSavedRecipients(ids, first, second, NotificationType.SUBSTITUTE_NO_CANDIDATE,
            "대체 근무 가능한 후보가 없습니다.")
    }

    @Test
    fun createSubstituteAcceptedNotifications() {
        val first = mock<WorkplaceMember>()
        val second = mock<WorkplaceMember>()
        val ids = listOf(100L, 200L)
        arrangeRecipients(ids, listOf(first, second))
        notificationService.createSubstituteAcceptedNotifications(ids)
        assertSavedRecipients(ids, first, second, NotificationType.SUBSTITUTE_ACCEPTED,
            "대체 근무 수락자가 생겼습니다. 최종 승인이 필요합니다.")
    }

    @Test
    fun createSubstituteAllRejectedNotifications() {
        val first = mock<WorkplaceMember>()
        val second = mock<WorkplaceMember>()
        val ids = listOf(100L, 200L)
        arrangeRecipients(ids, listOf(first, second))
        notificationService.createSubstituteAllRejectedNotifications(ids)
        assertSavedRecipients(ids, first, second, NotificationType.SUBSTITUTE_ALL_REJECTED,
            "대체 근무 요청을 받은 모든 수신자가 거절했습니다.")
    }

    @Test
    fun createSubstituteApprovedNotifications() {
        val requesterMemberId = 100L
        val acceptedMemberId = 200L
        val requester = mock<WorkplaceMember>()
        val acceptedMember = mock<WorkplaceMember>()
        whenever(requester.id).thenReturn(requesterMemberId)
        whenever(acceptedMember.id).thenReturn(acceptedMemberId)
        val ids = listOf(requesterMemberId, acceptedMemberId)
        // Deliberately return members opposite to the requested ID order.
        arrangeRecipients(ids, listOf(acceptedMember, requester))
        notificationService.createSubstituteApprovedNotifications(requesterMemberId, acceptedMemberId)
        verify(workplaceMemberRepository).findAllById(ids)
        val captor = argumentCaptor<Notification>()
        verify(notificationRepository, times(2)).save(captor.capture())
        val savedNotifications = captor.allValues
        assertThat(savedNotifications).hasSize(2)
        val requesterNotification = savedNotifications.first { it.recipientMember == requester }
        val acceptedNotification = savedNotifications.first { it.recipientMember == acceptedMember }
        assertThat(requesterNotification.type).isEqualTo(NotificationType.SUBSTITUTE_APPROVED)
        assertThat(requesterNotification.message)
            .isEqualTo("요청한 대체 근무 변경이 최종 승인되었습니다.")
        assertThat(acceptedNotification.type).isEqualTo(NotificationType.SUBSTITUTE_APPROVED)
        assertThat(acceptedNotification.message)
            .isEqualTo("수락한 대체 근무가 공식 근무로 확정되었습니다.")
    }

    @Test
    fun createSubstituteManagerClosedNotifications() {
        val first = mock<WorkplaceMember>()
        val second = mock<WorkplaceMember>()
        val ids = listOf(100L, 200L)
        arrangeRecipients(ids, listOf(first, second))
        notificationService.createSubstituteManagerClosedNotifications(ids)
        assertSavedRecipients(ids, first, second, NotificationType.SUBSTITUTE_MANAGER_CLOSED,
            "관리자가 대체 근무 요청을 종료했습니다.")
    }

    @Test
    @DisplayName("근무표 공개 알림 수신자 일부를 찾지 못하면 예외가 나고 알림을 저장하지 않는다")
    fun createSchedulePublishedNotificationsFailsWhenRecipientMissing() {
        val ids = listOf(100L, 200L)
        whenever(workplaceMemberRepository.findAllById(ids)).thenReturn(listOf(mock<WorkplaceMember>()))
        assertMissingRecipientFails(ids, "Schedule 공개 알림 수신자를 찾을 수 없습니다.") {
            notificationService.createSchedulePublishedNotifications(ids)
        }
    }

    @Test
    @DisplayName("대타 요청 알림 수신자 일부를 찾지 못하면 예외가 나고 알림을 저장하지 않는다")
    fun createSubstituteRequestReceivedNotificationsFailsWhenRecipientMissing() {
        val ids = listOf(100L, 200L)
        whenever(workplaceMemberRepository.findAllById(ids)).thenReturn(listOf(mock<WorkplaceMember>()))
        assertMissingRecipientFails(ids, "대타 요청 알림 수신자를 찾을 수 없습니다.") {
            notificationService.createSubstituteRequestReceivedNotifications(ids)
        }
    }

    @Test
    @DisplayName("대타 후보 없음 알림 수신자 일부를 찾지 못하면 예외가 나고 알림을 저장하지 않는다")
    fun createSubstituteNoCandidateNotificationsFailsWhenRecipientMissing() {
        val ids = listOf(100L, 200L)
        whenever(workplaceMemberRepository.findAllById(ids)).thenReturn(listOf(mock<WorkplaceMember>()))
        assertMissingRecipientFails(ids, "대타 후보 없음 알림 수신자를 찾을 수 없습니다.") {
            notificationService.createSubstituteNoCandidateNotifications(ids)
        }
    }

    @Test
    @DisplayName("대타 수락 알림 수신자 일부를 찾지 못하면 예외가 나고 알림을 저장하지 않는다")
    fun createSubstituteAcceptedNotificationsFailsWhenRecipientMissing() {
        val ids = listOf(100L, 200L)
        whenever(workplaceMemberRepository.findAllById(ids)).thenReturn(listOf(mock<WorkplaceMember>()))
        assertMissingRecipientFails(ids, "대타 수락 알림 수신자를 찾을 수 없습니다.") {
            notificationService.createSubstituteAcceptedNotifications(ids)
        }
    }

    @Test
    @DisplayName("대타 후보 전원 거절 알림 수신자 일부를 찾지 못하면 예외가 나고 알림을 저장하지 않는다")
    fun createSubstituteAllRejectedNotificationsFailsWhenRecipientMissing() {
        val ids = listOf(100L, 200L)
        whenever(workplaceMemberRepository.findAllById(ids)).thenReturn(listOf(mock<WorkplaceMember>()))
        assertMissingRecipientFails(ids, "대타 후보 전원 거절 알림 수신자를 찾을 수 없습니다.") {
            notificationService.createSubstituteAllRejectedNotifications(ids)
        }
    }

    @Test
    @DisplayName("대체 근무 승인 알림 수신자 일부를 찾지 못하면 예외가 나고 알림을 저장하지 않는다")
    fun createSubstituteApprovedNotificationsFailsWhenRecipientMissing() {
        val requesterMemberId = 100L
        val acceptedMemberId = 200L
        val ids = listOf(requesterMemberId, acceptedMemberId)
        val requester = mock<WorkplaceMember>()
        whenever(workplaceMemberRepository.findAllById(ids)).thenReturn(listOf(requester))
        assertMissingRecipientFails(ids, "대체 근무 승인 알림 수신자를 찾을 수 없습니다.") {
            notificationService.createSubstituteApprovedNotifications(requesterMemberId, acceptedMemberId)
        }
    }

    @Test
    @DisplayName("대체 근무 관리자 종료 알림 수신자 일부를 찾지 못하면 예외가 나고 알림을 저장하지 않는다")
    fun createSubstituteManagerClosedNotificationsFailsWhenRecipientMissing() {
        val ids = listOf(100L, 200L)
        whenever(workplaceMemberRepository.findAllById(ids)).thenReturn(listOf(mock<WorkplaceMember>()))
        assertMissingRecipientFails(ids, "대체 근무 관리자 종료 알림 수신자를 찾을 수 없습니다.") {
            notificationService.createSubstituteManagerClosedNotifications(ids)
        }
    }

    private fun assertMissingRecipientFails(ids: List<Long>, expectedMessage: String, action: () -> Unit) {
        val thrown = catchThrowable { action() }

        assertThat(thrown)
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessage(expectedMessage)
        verify(workplaceMemberRepository, times(1)).findAllById(ids)
        verify(notificationRepository, never()).save(any<Notification>())
    }

    private fun arrangeRecipients(ids: List<Long>, members: List<WorkplaceMember>) {
        whenever(workplaceMemberRepository.findAllById(ids)).thenReturn(members)
        stubSaveReturningArgument()
    }

    private fun stubSaveReturningArgument() {
        whenever(notificationRepository.save(any<Notification>()))
            .thenAnswer { invocation -> invocation.getArgument(0) }
    }

    private fun assertSavedRecipients(
        ids: List<Long>, first: WorkplaceMember, second: WorkplaceMember,
        type: NotificationType, message: String,
    ) {
        verify(workplaceMemberRepository).findAllById(ids)
        val captor = argumentCaptor<Notification>()
        verify(notificationRepository, times(2)).save(captor.capture())
        val savedNotifications = captor.allValues
        assertThat(savedNotifications.map { it.recipientMember })
            .containsExactlyInAnyOrder(first, second)
        assertThat(savedNotifications.map { it.type }).containsOnly(type)
        assertThat(savedNotifications.map { it.message }).containsOnly(message)
    }
}
