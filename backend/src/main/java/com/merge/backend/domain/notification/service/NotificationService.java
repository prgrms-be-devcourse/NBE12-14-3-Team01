package com.merge.backend.domain.notification.service;

import com.merge.backend.domain.notification.dto.NotificationReadResponse;
import com.merge.backend.domain.notification.dto.NotificationResponse;
import com.merge.backend.domain.notification.entity.Notification;
import com.merge.backend.domain.notification.entity.NotificationType;
import com.merge.backend.domain.notification.exception.NotificationErrorCode;
import com.merge.backend.domain.notification.repository.NotificationRepository;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final WorkplaceMemberRepository workplaceMemberRepository;

    private final Clock clock;

    private static final String SCHEDULE_PUBLISHED_MESSAGE =
        "새로운 주간 근무표가 공개되었습니다.";
    private static final String SUBSTITUTE_REQUEST_RECEIVED_MESSAGE =
        "새로운 대타 요청이 도착했습니다.";
    private static final String SUBSTITUTE_NO_CANDIDATE_MESSAGE =
        "대체 근무 가능한 후보가 없습니다.";
    private static final String SUBSTITUTE_ACCEPTED_MESSAGE =
        "대체 근무 수락자가 생겼습니다. 최종 승인이 필요합니다.";
    private static final String SUBSTITUTE_ALL_REJECTED_MESSAGE =
        "대체 근무 요청을 받은 모든 수신자가 거절했습니다.";
    private static final String SUBSTITUTE_APPROVED_REQUESTER_MESSAGE =
        "요청한 대체 근무 변경이 최종 승인되었습니다.";
    private static final String SUBSTITUTE_APPROVED_ACCEPTED_MESSAGE =
        "수락한 대체 근무가 공식 근무로 확정되었습니다.";
    private static final String SUBSTITUTE_MANAGER_CLOSED_MESSAGE =
        "관리자가 대체 근무 요청을 종료했습니다.";

    public Notification create(
        WorkplaceMember recipientMember,
        NotificationType type,
        String message
    ) {
        Notification notification = new Notification(
            recipientMember,
            type,
            message
        );

        return notificationRepository.save(notification);
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void createSchedulePublishedNotifications(
        List<Long> recipientMemberIds
    ) {
        List<WorkplaceMember> recipientMembers =
            workplaceMemberRepository.findAllById(
                recipientMemberIds
            );

        if (recipientMembers.size()
            != recipientMemberIds.size()) {

            throw new IllegalStateException(
                "Schedule 공개 알림 수신자를 찾을 수 없습니다."
            );
        }

        for (WorkplaceMember recipientMember : recipientMembers) {
            create(
                recipientMember,
                NotificationType.SCHEDULE_PUBLISHED,
                SCHEDULE_PUBLISHED_MESSAGE
            );
        }
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void createSubstituteRequestReceivedNotifications(
        List<Long> candidateMemberIds
    ) {
        List<WorkplaceMember> candidateMembers =
            workplaceMemberRepository.findAllById(
                candidateMemberIds
            );

        if (candidateMembers.size()
            != candidateMemberIds.size()) {

            throw new IllegalStateException(
                "대타 요청 알림 수신자를 찾을 수 없습니다."
            );
        }

        for (WorkplaceMember candidateMember : candidateMembers) {
            create(
                candidateMember,
                NotificationType.SUBSTITUTE_REQUEST_RECEIVED,
                SUBSTITUTE_REQUEST_RECEIVED_MESSAGE
            );
        }
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void createSubstituteNoCandidateNotifications(
        List<Long> managerMemberIds
    ) {
        List<WorkplaceMember> managerMembers =
            workplaceMemberRepository.findAllById(
                managerMemberIds
            );

        if (managerMembers.size()
            != managerMemberIds.size()) {

            throw new IllegalStateException(
                "대타 후보 없음 알림 수신자를 찾을 수 없습니다."
            );
        }

        for (WorkplaceMember managerMember : managerMembers) {
            create(
                managerMember,
                NotificationType.SUBSTITUTE_NO_CANDIDATE,
                SUBSTITUTE_NO_CANDIDATE_MESSAGE
            );
        }
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void createSubstituteAcceptedNotifications(
        List<Long> managerMemberIds
    ) {
        List<WorkplaceMember> managerMembers =
            workplaceMemberRepository.findAllById(
                managerMemberIds
            );

        if (managerMembers.size()
            != managerMemberIds.size()) {

            throw new IllegalStateException(
                "대타 수락 알림 수신자를 찾을 수 없습니다."
            );
        }

        for (WorkplaceMember managerMember : managerMembers) {
            create(
                managerMember,
                NotificationType.SUBSTITUTE_ACCEPTED,
                SUBSTITUTE_ACCEPTED_MESSAGE
            );
        }
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void createSubstituteAllRejectedNotifications(
        List<Long> recipientMemberIds
    ) {
        List<WorkplaceMember> recipientMembers =
            workplaceMemberRepository.findAllById(
                recipientMemberIds
            );

        if (recipientMembers.size()
            != recipientMemberIds.size()) {

            throw new IllegalStateException(
                "대타 후보 전원 거절 알림 수신자를 찾을 수 없습니다."
            );
        }

        for (WorkplaceMember recipientMember : recipientMembers) {
            create(
                recipientMember,
                NotificationType.SUBSTITUTE_ALL_REJECTED,
                SUBSTITUTE_ALL_REJECTED_MESSAGE
            );
        }
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void createSubstituteApprovedNotifications(
        Long requesterMemberId,
        Long acceptedMemberId
    ) {
        List<Long> recipientMemberIds =
            List.of(
                requesterMemberId,
                acceptedMemberId
            );

        List<WorkplaceMember> recipientMembers =
            workplaceMemberRepository.findAllById(
                recipientMemberIds
            );

        if (recipientMembers.size()
            != recipientMemberIds.size()) {

            throw new IllegalStateException(
                "대체 근무 승인 알림 수신자를 찾을 수 없습니다."
            );
        }

        WorkplaceMember requesterMember =
            recipientMembers.stream()
                .filter(member ->
                    member.getId().equals(
                        requesterMemberId
                    )
                )
                .findFirst()
                .orElseThrow(() ->
                    new IllegalStateException(
                        "대체 근무 승인 요청자를 찾을 수 없습니다."
                    )
                );

        WorkplaceMember acceptedMember =
            recipientMembers.stream()
                .filter(member ->
                    member.getId().equals(
                        acceptedMemberId
                    )
                )
                .findFirst()
                .orElseThrow(() ->
                    new IllegalStateException(
                        "대체 근무 승인 수락자를 찾을 수 없습니다."
                    )
                );

        create(
            requesterMember,
            NotificationType.SUBSTITUTE_APPROVED,
            SUBSTITUTE_APPROVED_REQUESTER_MESSAGE
        );

        create(
            acceptedMember,
            NotificationType.SUBSTITUTE_APPROVED,
            SUBSTITUTE_APPROVED_ACCEPTED_MESSAGE
        );
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void createSubstituteManagerClosedNotifications(
        List<Long> recipientMemberIds
    ) {
        List<WorkplaceMember> recipientMembers =
            workplaceMemberRepository.findAllById(
                recipientMemberIds
            );

        if (recipientMembers.size()
            != recipientMemberIds.size()) {

            throw new IllegalStateException(
                "대체 근무 관리자 종료 알림 수신자를 찾을 수 없습니다."
            );
        }

        for (WorkplaceMember recipientMember
            : recipientMembers) {

            create(
                recipientMember,
                NotificationType.SUBSTITUTE_MANAGER_CLOSED,
                SUBSTITUTE_MANAGER_CLOSED_MESSAGE
            );
        }
    }

    @Transactional
    public NotificationReadResponse markAsRead(
        Long actorUserId,
        Long notificationId
    ) {
        Notification notification =
            notificationRepository.findByIdForUpdate(notificationId);

        if (notification == null) {
            throw new BusinessException(
                NotificationErrorCode.NOT_FOUND_NOTIFICATION
            );
        }

        WorkplaceMember recipientMember = notification.getRecipientMember();

        Long recipientUserId = recipientMember.getUser().getId();

        if (!recipientUserId.equals(actorUserId)
            || recipientMember.getLeftAt() != null) {
            throw new BusinessException(
                NotificationErrorCode.FORBIDDEN_ACCESS
            );
        }

        notification.markAsRead(LocalDateTime.now(clock));

        return NotificationReadResponse.from(
            notification
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(
        Long actorUserId
    ) {
        return notificationRepository
            .findAllByUserId(actorUserId)
            .stream()
            .map(NotificationResponse::from)
            .toList();
    }



}
