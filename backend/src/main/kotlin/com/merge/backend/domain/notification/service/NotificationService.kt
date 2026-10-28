package com.merge.backend.domain.notification.service

import com.merge.backend.domain.notification.dto.NotificationReadResponse
import com.merge.backend.domain.notification.dto.NotificationResponse
import com.merge.backend.domain.notification.entity.Notification
import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.notification.exception.NotificationErrorCode
import com.merge.backend.domain.notification.repository.NotificationRepository
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.global.exception.BusinessException
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val workplaceMemberRepository: WorkplaceMemberRepository,
    private val clock: Clock,
) {
    fun create(
        recipientMember: WorkplaceMember,
        type: NotificationType,
        message: String,
    ): Notification {
        val notification = Notification(
            recipientMember,
            type,
            message,
        )
        return notificationRepository.save(notification)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createSchedulePublishedNotifications(recipientMemberIds: List<Long>) {
        val recipientMembers = workplaceMemberRepository.findAllRecipientsOrThrow(
            recipientMemberIds,
            "Schedule 공개 알림 수신자를 찾을 수 없습니다.",
        )

        recipientMembers.forEach { recipientMember ->
            create(
                recipientMember,
                NotificationType.SCHEDULE_PUBLISHED,
                SCHEDULE_PUBLISHED_MESSAGE,
            )
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createSubstituteRequestReceivedNotifications(candidateMemberIds: List<Long>) {
        val candidateMembers = workplaceMemberRepository.findAllRecipientsOrThrow(
            candidateMemberIds,
            "대타 요청 알림 수신자를 찾을 수 없습니다.",
        )

        candidateMembers.forEach { candidateMember ->
            create(
                candidateMember,
                NotificationType.SUBSTITUTE_REQUEST_RECEIVED,
                SUBSTITUTE_REQUEST_RECEIVED_MESSAGE,
            )
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createSubstituteNoCandidateNotifications(managerMemberIds: List<Long>) {
        val managerMembers = workplaceMemberRepository.findAllRecipientsOrThrow(
            managerMemberIds,
            "대타 후보 없음 알림 수신자를 찾을 수 없습니다.",
        )

        managerMembers.forEach { managerMember ->
            create(
                managerMember,
                NotificationType.SUBSTITUTE_NO_CANDIDATE,
                SUBSTITUTE_NO_CANDIDATE_MESSAGE,
            )
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createSubstituteAcceptedNotifications(managerMemberIds: List<Long>) {
        val managerMembers = workplaceMemberRepository.findAllRecipientsOrThrow(
            managerMemberIds,
            "대타 수락 알림 수신자를 찾을 수 없습니다.",
        )

        managerMembers.forEach { managerMember ->
            create(
                managerMember,
                NotificationType.SUBSTITUTE_ACCEPTED,
                SUBSTITUTE_ACCEPTED_MESSAGE,
            )
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createSubstituteAllRejectedNotifications(recipientMemberIds: List<Long>) {
        val recipientMembers = workplaceMemberRepository.findAllRecipientsOrThrow(
            recipientMemberIds,
            "대타 후보 전원 거절 알림 수신자를 찾을 수 없습니다.",
        )

        recipientMembers.forEach { recipientMember ->
            create(
                recipientMember,
                NotificationType.SUBSTITUTE_ALL_REJECTED,
                SUBSTITUTE_ALL_REJECTED_MESSAGE,
            )
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createSubstituteApprovedNotifications(
        requesterMemberId: Long?,
        acceptedMemberId: Long?,
    ) {
        val recipientMemberIds = listOf(
            requireId(requesterMemberId),
            requireId(acceptedMemberId),
        )
        val recipientMembers = workplaceMemberRepository.findAllRecipientsOrThrow(
            recipientMemberIds,
            "대체 근무 승인 알림 수신자를 찾을 수 없습니다.",
        )

        val requesterMember = recipientMembers.firstOrNull { member ->
            member.id == requesterMemberId
        } ?: throw IllegalStateException("대체 근무 승인 요청자를 찾을 수 없습니다.")

        val acceptedMember = recipientMembers.firstOrNull { member ->
            member.id == acceptedMemberId
        } ?: throw IllegalStateException("대체 근무 승인 수락자를 찾을 수 없습니다.")

        create(
            requesterMember,
            NotificationType.SUBSTITUTE_APPROVED,
            SUBSTITUTE_APPROVED_REQUESTER_MESSAGE,
        )
        create(
            acceptedMember,
            NotificationType.SUBSTITUTE_APPROVED,
            SUBSTITUTE_APPROVED_ACCEPTED_MESSAGE,
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createSubstituteManagerClosedNotifications(recipientMemberIds: List<Long>) {
        val recipientMembers = workplaceMemberRepository.findAllRecipientsOrThrow(
            recipientMemberIds,
            "대체 근무 관리자 종료 알림 수신자를 찾을 수 없습니다.",
        )

        recipientMembers.forEach { recipientMember ->
            create(
                recipientMember,
                NotificationType.SUBSTITUTE_MANAGER_CLOSED,
                SUBSTITUTE_MANAGER_CLOSED_MESSAGE,
            )
        }
    }

    // 새 알림 (공개 후 근무 변경, 후보 자격 상실, 가입). 받는 사람·문구는 업무 Transaction 안에서 정해 둔 값 그대로 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createPlannedNotification(planned: PlannedNotification) {
        val recipientMember = workplaceMemberRepository.findById(planned.recipientMemberId)
            .orElseThrow { IllegalStateException("알림 수신자를 찾을 수 없습니다. memberId=${planned.recipientMemberId}") }

        create(recipientMember, planned.type, planned.message)
    }

    @Transactional
    fun markAsRead(
        actorUserId: Long?,
        notificationId: Long?,
    ): NotificationReadResponse {
        val notification = notificationRepository.findByIdForUpdate(
            requireId(notificationId),
        ) ?: throw BusinessException(NotificationErrorCode.NOT_FOUND_NOTIFICATION)

        val recipientMember = notification.recipientMember
        val recipientUserId = recipientMember.user.id

        if (recipientUserId != actorUserId || recipientMember.leftAt != null) {
            throw BusinessException(NotificationErrorCode.FORBIDDEN_ACCESS)
        }

        notification.markAsRead(LocalDateTime.now(clock))
        return NotificationReadResponse.from(notification)
    }

    @Transactional(readOnly = true)
    fun getNotifications(actorUserId: Long?): List<NotificationResponse> =
        notificationRepository.findAllByUserId(requireId(actorUserId))
            .map { notification -> NotificationResponse.from(notification) }

    private fun requireId(id: Long?): Long = id ?: throw NullPointerException()

    private companion object {
        private const val SCHEDULE_PUBLISHED_MESSAGE = "새로운 주간 근무표가 공개되었습니다."
        private const val SUBSTITUTE_REQUEST_RECEIVED_MESSAGE = "새로운 대타 요청이 도착했습니다."
        private const val SUBSTITUTE_NO_CANDIDATE_MESSAGE = "대체 근무 가능한 후보가 없습니다."
        private const val SUBSTITUTE_ACCEPTED_MESSAGE = "대체 근무 수락자가 생겼습니다. 최종 승인이 필요합니다."
        private const val SUBSTITUTE_ALL_REJECTED_MESSAGE = "대체 근무 요청을 받은 모든 수신자가 거절했습니다."
        private const val SUBSTITUTE_APPROVED_REQUESTER_MESSAGE = "요청한 대체 근무 변경이 최종 승인되었습니다."
        private const val SUBSTITUTE_APPROVED_ACCEPTED_MESSAGE = "수락한 대체 근무가 공식 근무로 확정되었습니다."
        private const val SUBSTITUTE_MANAGER_CLOSED_MESSAGE = "관리자가 대체 근무 요청을 종료했습니다."
    }
}

// 받을 사람을 ID 목록 그대로 한 번에 조회한다. 하나라도 찾지 못하면 IllegalStateException을 던져
// 그 사건의 알림을 하나도 만들지 않는다 (퇴사 여부·중복 ID는 따로 거르지 않음)
private fun WorkplaceMemberRepository.findAllRecipientsOrThrow(
    ids: List<Long>,
    notFoundMessage: String,
): List<WorkplaceMember> =
    findAllById(ids).also { members ->
        check(members.size == ids.size) { notFoundMessage }
    }
