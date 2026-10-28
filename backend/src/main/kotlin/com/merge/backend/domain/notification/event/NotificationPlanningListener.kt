package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.notification.service.PlannedNotification
import com.merge.backend.domain.notification.service.ShiftChangeNotificationPlanner
import com.merge.backend.domain.shift.event.PublishedShiftChangedEvent
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.substitute.event.SubstituteCandidateIneligibleEvent
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.event.WorkplaceMemberJoinedEvent
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

// 새 알림의 받는 사람을 업무 Transaction 안(발행 시점, Lock 이후)에서 정한다 (NOTIFICATION-TYPES §3-2 R1, §3-2-1).
// 동기 Listener라 업무와 같은 Transaction에서 읽기만 하고, 실패해도 업무를 막지 않도록 예외를 삼킨다 (R2)
@Component
class NotificationPlanningListener(
    private val shiftRepository: ShiftRepository,
    private val substituteRequestRepository: SubstituteRequestRepository,
    private val workplaceMemberRepository: WorkplaceMemberRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    @EventListener
    fun planPublishedShiftChanged(event: PublishedShiftChangedEvent) = planSafely(event) {
        val shift = shiftRepository.findById(requireNotNull(event.shiftId)).orElseThrow()
        val managerIds = currentManagerIds(requireNotNull(shift.schedule.workplace.id))
        val candidateIds = listOfNotNull(event.previousMemberId, event.newMemberId) +
            event.closedRequests.flatMap { listOfNotNull(it.requesterMemberId, it.acceptedMemberId) } +
            managerIds

        ShiftChangeNotificationPlanner.plan(
            kind = event.kind,
            shiftStartAt = shift.startAt,
            shiftEndAt = shift.endAt,
            previousMemberId = event.previousMemberId,
            newMemberId = event.newMemberId,
            actorMemberId = event.actorMemberId,
            closedRequests = event.closedRequests,
            currentManagerMemberIds = managerIds,
            currentMemberIds = currentMemberIds(candidateIds),
        )
    }

    // 거절로 응답 가능한 후보가 없어진 종료 (#92). 받는 사람은 V4가 정한 요청자 + 현재 관리자에서 현재 소속만 남긴다
    @EventListener
    fun planCandidateIneligible(event: SubstituteCandidateIneligibleEvent) = planSafely(event) {
        val request = substituteRequestRepository.findById(event.requestId).orElseThrow()
        val shiftLabel = ShiftChangeNotificationPlanner.shiftLabel(request.shift.startAt, request.shift.endAt)
        val requesterId = request.requesterMember.id

        currentMemberIds(event.recipientMemberIds).map { recipientId ->
            val message = if (recipientId == requesterId) {
                "$shiftLabel 근무의 대타 요청이 응답할 수 있는 후보가 없어 종료되었습니다. 근무는 그대로이니 필요하면 다시 요청해 주세요."
            } else {
                "$shiftLabel 근무의 대타 요청이 응답할 수 있는 후보가 없어 종료되었습니다."
            }
            PlannedNotification(recipientId, NotificationType.SUBSTITUTE_CANDIDATE_INELIGIBLE, message)
        }
    }

    // 가입·재가입 → 그 근무지의 현재 관리자. 같은 근무지에 끝난 소속이 있으면 재가입 (ML-H2 H2-10·H2-11)
    @EventListener
    fun planMemberJoined(event: WorkplaceMemberJoinedEvent) = planSafely(event) {
        val member = workplaceMemberRepository.findById(requireNotNull(event.memberId)).orElseThrow()
        val workplaceId = requireNotNull(event.workplaceId)
        val userId = requireNotNull(member.user.id)
        val rejoined = workplaceMemberRepository.findAllByUser_IdAndLeftAtIsNotNullOrderByLeftAtDescIdDesc(userId)
            .any { it.workplace.id == workplaceId }
        val message = ShiftChangeNotificationPlanner.memberJoinedMessage(member.user.name, rejoined)

        currentManagerIds(workplaceId).map { PlannedNotification(it, NotificationType.WORKPLACE_MEMBER_JOINED, message) }
    }

    private fun currentManagerIds(workplaceId: Long): List<Long> =
        workplaceMemberRepository.findAllByWorkplace_IdAndRoleAndLeftAtIsNull(workplaceId, WorkplaceRole.MANAGER)
            .mapNotNull { it.id }

    private fun currentMemberIds(ids: Collection<Long>): Set<Long> =
        workplaceMemberRepository.findAllById(ids.toSet())
            .filter { it.leftAt == null }
            .mapNotNull { it.id }
            .toSet()

    private fun planSafely(event: Any, plan: () -> List<PlannedNotification>) {
        try {
            val notifications = plan()
            if (notifications.isNotEmpty()) {
                applicationEventPublisher.publishEvent(PlannedNotificationsEvent(event.toString(), notifications))
            }
        } catch (e: Exception) {
            log.error("알림 받는 사람 계산 실패. event={}", event, e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(NotificationPlanningListener::class.java)
    }
}
