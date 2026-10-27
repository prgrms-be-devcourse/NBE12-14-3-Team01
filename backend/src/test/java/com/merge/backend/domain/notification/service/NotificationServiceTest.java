package com.merge.backend.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.merge.backend.domain.notification.dto.NotificationReadResponse;
import com.merge.backend.domain.notification.dto.NotificationResponse;
import com.merge.backend.domain.notification.entity.Notification;
import com.merge.backend.domain.notification.entity.NotificationType;
import com.merge.backend.domain.notification.exception.NotificationErrorCode;
import com.merge.backend.domain.notification.repository.NotificationRepository;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.global.config.TestClock;
import com.merge.backend.global.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private WorkplaceMemberRepository workplaceMemberRepository;
    private NotificationService notificationService;
    private TestClock testClock;

    @BeforeEach
    void setUp() {
        notificationRepository =
            mock(NotificationRepository.class);

        workplaceMemberRepository =
            mock(WorkplaceMemberRepository.class);

        testClock = new TestClock(
            Instant.parse("2026-09-15T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
        );

        notificationService = new NotificationService(
            notificationRepository,
            workplaceMemberRepository,
            testClock
        );
    }

    @Test
    @DisplayName("알림을 생성한다")
    void createNotification() {
        WorkplaceMember recipientMember =
            mock(WorkplaceMember.class);

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Notification notification =
            notificationService.create(
                recipientMember,
                NotificationType.SCHEDULE_PUBLISHED,
                "새로운 주간 근무표가 공개되었습니다."
            );

        assertThat(notification.getRecipientMember())
            .isSameAs(recipientMember);

        assertThat(notification.getType())
            .isEqualTo(
                NotificationType.SCHEDULE_PUBLISHED
            );

        assertThat(notification.getMessage())
            .isEqualTo(
                "새로운 주간 근무표가 공개되었습니다."
            );

        assertThat(notification.getReadAt()).isNull();

        verify(notificationRepository)
            .save(any(Notification.class));
    }

    @Test
    @DisplayName("알림을 읽음 처리한다")
    void markAsRead() {
        Long actorUserId = 1L;
        Long notificationId = 100L;

        User recipientUser =
            mock(User.class);

        WorkplaceMember recipientMember =
            mock(WorkplaceMember.class);

        when(recipientMember.getUser())
            .thenReturn(recipientUser);

        when(recipientUser.getId())
            .thenReturn(actorUserId);

        Notification notification = new Notification(
            recipientMember,
            NotificationType.SCHEDULE_PUBLISHED,
            "새로운 주간 근무표가 공개되었습니다."
        );

        when(
            notificationRepository.findByIdForUpdate(
                notificationId
            )
        ).thenReturn(
            notification
        );

        NotificationReadResponse result =
            notificationService.markAsRead(
                actorUserId,
                notificationId
            );

        LocalDateTime expectedReadAt =
            LocalDateTime.of(
                2026,
                9,
                15,
                10,
                0
            );

        assertThat(notification.getReadAt())
            .isEqualTo(expectedReadAt);

        assertThat(result.getReadAt())
            .isEqualTo(expectedReadAt);

        verify(notificationRepository)
            .findByIdForUpdate(
                notificationId
            );
    }

    @Test
    @DisplayName("존재하지 않는 알림은 읽음 처리할 수 없다")
    void markAsReadFailsWhenNotificationNotFound() {
        Long actorUserId = 1L;
        Long notificationId = 100L;

        when(
            notificationRepository.findByIdForUpdate(
                notificationId
            )
        ).thenReturn(
            null
        );

        assertThatThrownBy(() ->
            notificationService.markAsRead(
                actorUserId,
                notificationId
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(
                        NotificationErrorCode.NOT_FOUND_NOTIFICATION
                    );
            });
    }

    @Test
    @DisplayName("다른 사용자의 알림은 읽음 처리할 수 없다")
    void markAsReadFailsWhenNotificationBelongsToAnotherUser() {
        Long actorUserId = 1L;
        Long recipientUserId = 2L;
        Long notificationId = 100L;

        User recipientUser =
            mock(User.class);

        WorkplaceMember recipientMember =
            mock(WorkplaceMember.class);

        when(recipientMember.getUser())
            .thenReturn(recipientUser);

        when(recipientUser.getId())
            .thenReturn(recipientUserId);

        Notification notification = new Notification(
            recipientMember,
            NotificationType.SCHEDULE_PUBLISHED,
            "새로운 주간 근무표가 공개되었습니다."
        );

        when(
            notificationRepository.findByIdForUpdate(
                notificationId
            )
        ).thenReturn(
            notification
        );

        assertThatThrownBy(() ->
            notificationService.markAsRead(
                actorUserId,
                notificationId
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(
                        NotificationErrorCode.FORBIDDEN_ACCESS
                    );
            });

        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    @DisplayName("퇴사한 근무지의 알림은 읽음 처리할 수 없다")
    void markAsReadFailsWhenRecipientMembershipIsInactive() {
        Long actorUserId = 1L;
        Long notificationId = 100L;

        User recipientUser =
            mock(User.class);

        WorkplaceMember recipientMember =
            mock(WorkplaceMember.class);

        when(recipientMember.getUser())
            .thenReturn(recipientUser);

        when(recipientUser.getId())
            .thenReturn(actorUserId);

        when(recipientMember.getLeftAt())
            .thenReturn(
                LocalDateTime.of(
                    2026,
                    9,
                    30,
                    10,
                    0
                )
            );

        Notification notification = new Notification(
            recipientMember,
            NotificationType.SCHEDULE_PUBLISHED,
            "새로운 주간 근무표가 공개되었습니다."
        );

        when(
            notificationRepository.findByIdForUpdate(
                notificationId
            )
        ).thenReturn(
            notification
        );

        assertThatThrownBy(() ->
            notificationService.markAsRead(
                actorUserId,
                notificationId
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(
                        NotificationErrorCode.FORBIDDEN_ACCESS
                    );
            });

        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    @DisplayName("내 알림 목록을 조회한다")
    void getNotifications() {
        Long actorUserId = 1L;

        Notification notification =
            mock(Notification.class);

        WorkplaceMember recipientMember =
            mock(WorkplaceMember.class);

        Workplace workplace =
            mock(Workplace.class);

        LocalDateTime createdAt =
            LocalDateTime.of(
                2026,
                9,
                15,
                10,
                0
            );

        when(notification.getId())
            .thenReturn(100L);

        when(notification.getRecipientMember())
            .thenReturn(recipientMember);

        when(recipientMember.getWorkplace())
            .thenReturn(workplace);

        when(workplace.getId())
            .thenReturn(10L);

        when(workplace.getName())
            .thenReturn("SWITCH 카페");

        when(notification.getType())
            .thenReturn(
                NotificationType.SCHEDULE_PUBLISHED
            );

        when(notification.getMessage())
            .thenReturn(
                "새로운 주간 근무표가 공개되었습니다."
            );

        when(notification.getCreateDate())
            .thenReturn(createdAt);

        when(notification.getReadAt())
            .thenReturn(null);

        when(notificationRepository.findAllByUserId(actorUserId))
            .thenReturn(List.of(notification));

        List<NotificationResponse> result =
            notificationService.getNotifications(
                actorUserId
            );

        assertThat(result).hasSize(1);

        NotificationResponse response =
            result.get(0);

        assertThat(response.getNotificationId())
            .isEqualTo(100L);

        assertThat(response.getWorkplaceId())
            .isEqualTo(10L);

        assertThat(response.getWorkplaceName())
            .isEqualTo("SWITCH 카페");

        assertThat(response.getType())
            .isEqualTo(
                NotificationType.SCHEDULE_PUBLISHED
            );

        assertThat(response.getMessage())
            .isEqualTo(
                "새로운 주간 근무표가 공개되었습니다."
            );

        assertThat(response.getCreatedAt())
            .isEqualTo(createdAt);

        assertThat(response.getReadAt())
            .isNull();

        verify(notificationRepository)
            .findAllByUserId(actorUserId);
    }

    @Test
    @DisplayName("읽은 알림과 읽지 않은 알림을 모두 조회한다")
    void getNotificationsIncludesReadAndUnreadNotifications() {
        Long actorUserId = 1L;

        Notification unreadNotification =
            mock(Notification.class);

        Notification readNotification =
            mock(Notification.class);

        WorkplaceMember unreadRecipientMember =
            mock(WorkplaceMember.class);

        WorkplaceMember readRecipientMember =
            mock(WorkplaceMember.class);

        Workplace workplace =
            mock(Workplace.class);

        LocalDateTime unreadCreatedAt =
            LocalDateTime.of(
                2026,
                9,
                15,
                11,
                0
            );

        LocalDateTime readCreatedAt =
            LocalDateTime.of(
                2026,
                9,
                15,
                10,
                0
            );

        LocalDateTime readAt =
            LocalDateTime.of(
                2026,
                9,
                15,
                10,
                30
            );

        when(unreadNotification.getId())
            .thenReturn(101L);

        when(unreadNotification.getRecipientMember())
            .thenReturn(unreadRecipientMember);

        when(unreadRecipientMember.getWorkplace())
            .thenReturn(workplace);

        when(unreadNotification.getType())
            .thenReturn(
                NotificationType.SCHEDULE_PUBLISHED
            );

        when(unreadNotification.getMessage())
            .thenReturn("새로운 주간 근무표가 공개되었습니다.");

        when(unreadNotification.getCreateDate())
            .thenReturn(unreadCreatedAt);

        when(unreadNotification.getReadAt())
            .thenReturn(null);

        when(readNotification.getId())
            .thenReturn(100L);

        when(readNotification.getRecipientMember())
            .thenReturn(readRecipientMember);

        when(readRecipientMember.getWorkplace())
            .thenReturn(workplace);

        when(readNotification.getType())
            .thenReturn(
                NotificationType.SUBSTITUTE_APPROVED
            );

        when(readNotification.getMessage())
            .thenReturn("대타 요청이 최종 승인되었습니다.");

        when(readNotification.getCreateDate())
            .thenReturn(readCreatedAt);

        when(readNotification.getReadAt())
            .thenReturn(readAt);

        when(workplace.getId())
            .thenReturn(10L);

        when(workplace.getName())
            .thenReturn("SWITCH 카페");

        when(notificationRepository.findAllByUserId(actorUserId))
            .thenReturn(
                List.of(
                    unreadNotification,
                    readNotification
                )
            );

        List<NotificationResponse> result =
            notificationService.getNotifications(
                actorUserId
            );

        assertThat(result).hasSize(2);

        assertThat(result.get(0).getNotificationId())
            .isEqualTo(101L);

        assertThat(result.get(0).getReadAt())
            .isNull();

        assertThat(result.get(1).getNotificationId())
            .isEqualTo(100L);

        assertThat(result.get(1).getReadAt())
            .isEqualTo(readAt);
    }

    @Test
    void createSchedulePublishedNotifications() {

        // given
        List<Long> recipientMemberIds =
            List.of(
                100L,
                200L
            );

        WorkplaceMember firstMember =
            mock(WorkplaceMember.class);

        WorkplaceMember secondMember =
            mock(WorkplaceMember.class);

        when(
            workplaceMemberRepository.findAllById(
                recipientMemberIds
            )
        ).thenReturn(
            List.of(
                firstMember,
                secondMember
            )
        );

        when(
            notificationRepository.save(
                any(Notification.class)
            )
        ).thenAnswer(
            invocation ->
                invocation.getArgument(0)
        );

        // when
        notificationService.createSchedulePublishedNotifications(
            recipientMemberIds
        );

        // then
        verify(workplaceMemberRepository)
            .findAllById(
                recipientMemberIds
            );

        ArgumentCaptor<Notification> notificationCaptor =
            ArgumentCaptor.forClass(
                Notification.class
            );

        verify(notificationRepository, times(2))
            .save(
                notificationCaptor.capture()
            );

        List<Notification> savedNotifications =
            notificationCaptor.getAllValues();

        assertThat(savedNotifications)
            .extracting(Notification::getRecipientMember)
            .containsExactlyInAnyOrder(
                firstMember,
                secondMember
            );

        assertThat(savedNotifications)
            .extracting(Notification::getType)
            .containsOnly(
                NotificationType.SCHEDULE_PUBLISHED
            );

        assertThat(savedNotifications)
            .extracting(Notification::getMessage)
            .containsOnly(
                "새로운 주간 근무표가 공개되었습니다."
            );
    }

    @Test
    void createSubstituteRequestReceivedNotifications() {

        // given
        List<Long> candidateMemberIds =
            List.of(
                100L,
                200L
            );

        WorkplaceMember firstCandidate =
            mock(WorkplaceMember.class);

        WorkplaceMember secondCandidate =
            mock(WorkplaceMember.class);

        when(
            workplaceMemberRepository.findAllById(
                candidateMemberIds
            )
        ).thenReturn(
            List.of(
                firstCandidate,
                secondCandidate
            )
        );

        when(
            notificationRepository.save(
                any(Notification.class)
            )
        ).thenAnswer(
            invocation ->
                invocation.getArgument(0)
        );

        // when
        notificationService.createSubstituteRequestReceivedNotifications(
            candidateMemberIds
        );

        // then
        verify(workplaceMemberRepository)
            .findAllById(
                candidateMemberIds
            );

        ArgumentCaptor<Notification> notificationCaptor =
            ArgumentCaptor.forClass(
                Notification.class
            );

        verify(notificationRepository, times(2))
            .save(
                notificationCaptor.capture()
            );

        List<Notification> savedNotifications =
            notificationCaptor.getAllValues();

        assertThat(savedNotifications)
            .extracting(Notification::getRecipientMember)
            .containsExactlyInAnyOrder(
                firstCandidate,
                secondCandidate
            );

        assertThat(savedNotifications)
            .extracting(Notification::getType)
            .containsOnly(
                NotificationType.SUBSTITUTE_REQUEST_RECEIVED
            );

        assertThat(savedNotifications)
            .extracting(Notification::getMessage)
            .containsOnly(
                "새로운 대타 요청이 도착했습니다."
            );
    }

    @Test
    void createSubstituteNoCandidateNotifications() {

        // given
        List<Long> managerMemberIds =
            List.of(
                100L,
                200L
            );

        WorkplaceMember firstManager =
            mock(WorkplaceMember.class);

        WorkplaceMember secondManager =
            mock(WorkplaceMember.class);

        when(
            workplaceMemberRepository.findAllById(
                managerMemberIds
            )
        ).thenReturn(
            List.of(
                firstManager,
                secondManager
            )
        );

        when(
            notificationRepository.save(
                any(Notification.class)
            )
        ).thenAnswer(
            invocation ->
                invocation.getArgument(0)
        );

        // when
        notificationService.createSubstituteNoCandidateNotifications(
            managerMemberIds
        );

        // then
        verify(workplaceMemberRepository)
            .findAllById(
                managerMemberIds
            );

        ArgumentCaptor<Notification> notificationCaptor =
            ArgumentCaptor.forClass(
                Notification.class
            );

        verify(notificationRepository, times(2))
            .save(
                notificationCaptor.capture()
            );

        List<Notification> savedNotifications =
            notificationCaptor.getAllValues();

        assertThat(savedNotifications)
            .extracting(Notification::getRecipientMember)
            .containsExactlyInAnyOrder(
                firstManager,
                secondManager
            );

        assertThat(savedNotifications)
            .extracting(Notification::getType)
            .containsOnly(
                NotificationType.SUBSTITUTE_NO_CANDIDATE
            );

        assertThat(savedNotifications)
            .extracting(Notification::getMessage)
            .containsOnly(
                "대체 근무 가능한 후보가 없습니다."
            );
    }

    @Test
    void createSubstituteAcceptedNotifications() {

        // given
        List<Long> managerMemberIds =
            List.of(
                100L,
                200L
            );

        WorkplaceMember firstManager =
            mock(WorkplaceMember.class);

        WorkplaceMember secondManager =
            mock(WorkplaceMember.class);

        when(
            workplaceMemberRepository.findAllById(
                managerMemberIds
            )
        ).thenReturn(
            List.of(
                firstManager,
                secondManager
            )
        );

        when(
            notificationRepository.save(
                any(Notification.class)
            )
        ).thenAnswer(
            invocation ->
                invocation.getArgument(0)
        );

        // when
        notificationService.createSubstituteAcceptedNotifications(
            managerMemberIds
        );

        // then
        verify(workplaceMemberRepository)
            .findAllById(
                managerMemberIds
            );

        ArgumentCaptor<Notification> notificationCaptor =
            ArgumentCaptor.forClass(
                Notification.class
            );

        verify(notificationRepository, times(2))
            .save(
                notificationCaptor.capture()
            );

        List<Notification> savedNotifications =
            notificationCaptor.getAllValues();

        assertThat(savedNotifications)
            .extracting(
                Notification::getRecipientMember
            )
            .containsExactlyInAnyOrder(
                firstManager,
                secondManager
            );

        assertThat(savedNotifications)
            .extracting(
                Notification::getType
            )
            .containsOnly(
                NotificationType.SUBSTITUTE_ACCEPTED
            );

        assertThat(savedNotifications)
            .extracting(
                Notification::getMessage
            )
            .containsOnly(
                "대체 근무 수락자가 생겼습니다. 최종 승인이 필요합니다."
            );
    }

    @Test
    void createSubstituteAllRejectedNotifications() {

        // given
        List<Long> recipientMemberIds =
            List.of(
                100L,
                200L
            );

        WorkplaceMember requester =
            mock(WorkplaceMember.class);

        WorkplaceMember manager =
            mock(WorkplaceMember.class);

        when(
            workplaceMemberRepository.findAllById(
                recipientMemberIds
            )
        ).thenReturn(
            List.of(
                requester,
                manager
            )
        );

        when(
            notificationRepository.save(
                any(Notification.class)
            )
        ).thenAnswer(
            invocation ->
                invocation.getArgument(0)
        );

        // when
        notificationService.createSubstituteAllRejectedNotifications(
            recipientMemberIds
        );

        // then
        verify(workplaceMemberRepository)
            .findAllById(
                recipientMemberIds
            );

        ArgumentCaptor<Notification> notificationCaptor =
            ArgumentCaptor.forClass(
                Notification.class
            );

        verify(notificationRepository, times(2))
            .save(
                notificationCaptor.capture()
            );

        List<Notification> savedNotifications =
            notificationCaptor.getAllValues();

        assertThat(savedNotifications)
            .extracting(
                Notification::getRecipientMember
            )
            .containsExactlyInAnyOrder(
                requester,
                manager
            );

        assertThat(savedNotifications)
            .extracting(
                Notification::getType
            )
            .containsOnly(
                NotificationType.SUBSTITUTE_ALL_REJECTED
            );

        assertThat(savedNotifications)
            .extracting(
                Notification::getMessage
            )
            .containsOnly(
                "대체 근무 요청을 받은 모든 수신자가 거절했습니다."
            );
    }

    @Test
    void createSubstituteApprovedNotifications() {

        // given
        Long requesterMemberId = 100L;
        Long acceptedMemberId = 200L;

        WorkplaceMember requester =
            mock(WorkplaceMember.class);

        WorkplaceMember acceptedMember =
            mock(WorkplaceMember.class);

        when(requester.getId())
            .thenReturn(requesterMemberId);

        when(acceptedMember.getId())
            .thenReturn(acceptedMemberId);

        List<Long> recipientMemberIds =
            List.of(
                requesterMemberId,
                acceptedMemberId
            );

        /*
         * 일부러 요청한 ID 순서와 반대로 반환한다.
         *
         * findAllById() 결과 순서에 의존하지 않고
         * ID로 요청자/수락자를 구분하는지 확인하기 위함.
         */
        when(
            workplaceMemberRepository.findAllById(
                recipientMemberIds
            )
        ).thenReturn(
            List.of(
                acceptedMember,
                requester
            )
        );

        when(
            notificationRepository.save(
                any(Notification.class)
            )
        ).thenAnswer(
            invocation ->
                invocation.getArgument(0)
        );

        // when
        notificationService.createSubstituteApprovedNotifications(
            requesterMemberId,
            acceptedMemberId
        );

        // then
        verify(workplaceMemberRepository)
            .findAllById(
                recipientMemberIds
            );

        ArgumentCaptor<Notification> notificationCaptor =
            ArgumentCaptor.forClass(
                Notification.class
            );

        verify(notificationRepository, times(2))
            .save(
                notificationCaptor.capture()
            );

        List<Notification> savedNotifications =
            notificationCaptor.getAllValues();

        assertThat(savedNotifications)
            .hasSize(2);

        Notification requesterNotification =
            savedNotifications.stream()
                .filter(notification ->
                    notification
                        .getRecipientMember()
                        .equals(requester)
                )
                .findFirst()
                .orElseThrow();

        Notification acceptedNotification =
            savedNotifications.stream()
                .filter(notification ->
                    notification
                        .getRecipientMember()
                        .equals(acceptedMember)
                )
                .findFirst()
                .orElseThrow();

        // 요청자 알림
        assertThat(requesterNotification.getType())
            .isEqualTo(
                NotificationType.SUBSTITUTE_APPROVED
            );

        assertThat(requesterNotification.getMessage())
            .isEqualTo(
                "요청한 대체 근무 변경이 최종 승인되었습니다."
            );

        // 수락자 알림
        assertThat(acceptedNotification.getType())
            .isEqualTo(
                NotificationType.SUBSTITUTE_APPROVED
            );

        assertThat(acceptedNotification.getMessage())
            .isEqualTo(
                "수락한 대체 근무가 공식 근무로 확정되었습니다."
            );
    }

    @Test
    void createSubstituteManagerClosedNotifications() {

        // given
        List<Long> recipientMemberIds =
            List.of(
                100L,
                200L
            );

        WorkplaceMember requester =
            mock(WorkplaceMember.class);

        WorkplaceMember candidate =
            mock(WorkplaceMember.class);

        when(
            workplaceMemberRepository.findAllById(
                recipientMemberIds
            )
        ).thenReturn(
            List.of(
                requester,
                candidate
            )
        );

        when(
            notificationRepository.save(
                any(Notification.class)
            )
        ).thenAnswer(
            invocation ->
                invocation.getArgument(0)
        );

        // when
        notificationService.createSubstituteManagerClosedNotifications(
            recipientMemberIds
        );

        // then
        verify(workplaceMemberRepository)
            .findAllById(
                recipientMemberIds
            );

        ArgumentCaptor<Notification> notificationCaptor =
            ArgumentCaptor.forClass(
                Notification.class
            );

        verify(notificationRepository, times(2))
            .save(
                notificationCaptor.capture()
            );

        List<Notification> savedNotifications =
            notificationCaptor.getAllValues();

        assertThat(savedNotifications)
            .extracting(
                Notification::getRecipientMember
            )
            .containsExactlyInAnyOrder(
                requester,
                candidate
            );

        assertThat(savedNotifications)
            .extracting(
                Notification::getType
            )
            .containsOnly(
                NotificationType.SUBSTITUTE_MANAGER_CLOSED
            );

        assertThat(savedNotifications)
            .extracting(
                Notification::getMessage
            )
            .containsOnly(
                "관리자가 대체 근무 요청을 종료했습니다."
            );
    }

}