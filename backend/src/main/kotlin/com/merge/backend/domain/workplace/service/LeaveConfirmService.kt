package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.dto.AcknowledgedUnavailableConflict
import com.merge.backend.domain.shift.dto.UnavailableOverlap
import com.merge.backend.domain.shift.service.MemberLeaveShiftPlan
import com.merge.backend.domain.shift.service.ShiftService
import com.merge.backend.domain.workplace.dto.request.LeaveConfirmRequest
import com.merge.backend.domain.workplace.dto.response.LeaveConfirmResponse
import com.merge.backend.domain.workplace.dto.response.LeavePreviewResponse
import com.merge.backend.domain.workplace.event.MemberLeaveConfirmedEvent
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.dto.ValidationErrorResponse
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.GlobalErrorCode
import java.time.Clock
import java.time.LocalDateTime
import jakarta.validation.Validator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class LeaveConfirmService(
    private val members: WorkplaceMemberService,
    private val contexts: LeavePreviewContextService,
    private val preparation: LeavePlanPreparation,
    private val locks: LeaveConfirmationLocks,
    private val factsService: LeavePreviewService,
    private val shifts: ShiftService,
    private val writer: LeaveMembershipWriter,
    private val integration: ObjectProvider<LeaveConfirmationIntegration>,
    private val clock: Clock,
    private val validator: Validator,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun confirm(actorUserId: Long, workplaceId: Long, memberId: Long, request: LeaveConfirmRequest): LeaveConfirmResponse {
        val actor = members.requireManager(actorUserId, workplaceId)
        members.requireLeaveTarget(actorUserId, workplaceId, memberId)
        // Controller를 거치지 않는 내부 호출도 동일한 입력 검증을 Lock 획득 전에 수행한다.
        validateRequest(request)
        val context = contexts.verify(request.previewContext.orEmpty(), actorUserId, workplaceId, memberId, clock.instant())
            ?: throw BusinessException(WorkplaceErrorCode.STALE_LEAVE_PLAN)
        val locked = locks.acquire(context.facts, requireNotNull(actor.id), workplaceId, memberId,
            request, LocalDateTime.now(clock))
        // Lock 대기 뒤의 시각·권한·상태로 다시 읽는다. 잠그지 않은 새 Row를 뒤늦게 잠그지 않는다.
        val evaluatedAt = clock.instant()
        val now = LocalDateTime.ofInstant(evaluatedAt, clock.zone)
        val current = factsService.readForConfirmation(actorUserId, workplaceId, memberId, now)
        val result = preparation.prepare(actorUserId, workplaceId, memberId, request, current,
            locked.references, evaluatedAt)
        val plan = when (result) {
            is LeavePlanPreparation.Result.Ready -> result.plan
            is LeavePlanPreparation.Result.OngoingShift -> throw BusinessException(
                WorkplaceErrorCode.ONGOING_SHIFT_PREVENTS_LEAVE, mapOf("endAt" to result.endAt))
            LeavePlanPreparation.Result.ReferenceNotFound -> throw BusinessException(WorkplaceErrorCode.WORKPLACE_MEMBER_NOT_FOUND)
            LeavePlanPreparation.Result.InvalidRequest -> throw BusinessException(GlobalErrorCode.VALIDATION_ERROR)
            LeavePlanPreparation.Result.InvalidContext,
            LeavePlanPreparation.Result.Stale -> throw BusinessException(WorkplaceErrorCode.STALE_LEAVE_PLAN)
        }
        if (!locked.covers(current)) throw BusinessException(WorkplaceErrorCode.STALE_LEAVE_PLAN)
        val bridge = integration.ifAvailable
        // V4를 빈 성공으로 대체하지 않는다. 업무 변경 전에 연동 부재를 확인한다.
        check(plan.candidacies.isEmpty() || bridge != null) { "V4 퇴사 후보 정리(#92) 연동이 필요합니다." }
        val shiftPlan = MemberLeaveShiftPlan(
            plan.reassignments.map { MemberLeaveShiftPlan.Reassignment(
                locked.shifts.getValue(it.shift.shiftId), locked.members.getValue(it.targetMemberId)) },
            plan.cancellations.map { locked.shifts.getValue(it.shiftId) },
            plan.draftShiftsToDelete.map { locked.shifts.getValue(it.shiftId) }
        )
        val acknowledgements = plan.acknowledgedUnavailableConflicts.map { acknowledgement ->
            AcknowledgedUnavailableConflict(acknowledgement.shiftId, acknowledgement.targetMemberId,
                acknowledgement.overlaps.orEmpty().filterNotNull().map { UnavailableOverlap(it.startAt, it.endAt) })
        }
        val conflicts = shifts.validateMemberLeavePlan(shiftPlan, acknowledgements)
        if (conflicts.isNotEmpty()) throw BusinessException(
            WorkplaceErrorCode.LEAVE_UNAVAILABLE_CONFLICT_CONFIRMATION_REQUIRED,
            ConfirmationRequiredResponse(true, conflicts)
        )

        val shiftResult = shifts.applyMemberLeavePlan(shiftPlan, locked.members.getValue(requireNotNull(actor.id)), now)
        val candidateResult = if (plan.candidacies.isEmpty()) LeaveConfirmationIntegration.CandidacyCleanup(emptySet(), 0)
            else requireNotNull(bridge).cleanupCandidacies(memberId, now, locked.requestIds)
        check(candidateResult.closedRequestIds.all { it in locked.requestIds }) { "V4 처리 범위가 잠근 요청을 벗어났습니다." }
        check(candidateResult.closedRequestIds == plan.candidacies.filter { it.wouldCloseRequest }.map { it.requestId }.toSet() &&
            candidateResult.ineligibleCandidacies == plan.candidacies.count { it.wouldBecomeIneligible }) {
            "V4 실제 처리 결과가 검증한 퇴사 영향과 다릅니다."
        }
        val completed = writer.complete(locked.members.getValue(memberId), now, plan.regenerateInviteCode)

        // 알림: leftAt 기록 뒤에 발행해야 퇴사자가 받는 사람(현재 소속)에서 빠진다. 저장은 Commit 후 (V5-N-20)
        applicationEventPublisher.publishEvent(
            MemberLeaveConfirmedEvent(
                workplaceId = workplaceId,
                leaverMemberId = memberId,
                actorMemberId = requireNotNull(actor.id),
                reassignments = plan.reassignments.map { MemberLeaveConfirmedEvent.Reassignment(it.shift.shiftId, it.targetMemberId) },
                cancelledShiftIds = plan.cancellations.map { it.shiftId },
                shiftChangedRequestsByShiftId = shiftResult.closedRequestsByShiftId,
                candidateIneligibleRequests = candidateResult.closedRequests,
            ),
        )
        return LeaveConfirmResponse(
            completed.leftAt,
            plan.reassignments.map { LeavePreviewResponse.WorkShift.from(it.shift.copy(memberId = it.targetMemberId)) },
            plan.cancellations.map { LeavePreviewResponse.WorkShift.from(it.copy(
                shiftStatus = com.merge.backend.domain.shift.entity.ShiftStatus.CANCELLED)) },
            plan.draftShiftsToDelete.map(LeavePreviewResponse.WorkShift::from),
            LeaveConfirmResponse.ClosedRequests.from(shiftResult.closedRequests.map { it.requestId }.toSet(),
                candidateResult.closedRequestIds), candidateResult.ineligibleCandidacies, completed.newInviteCode
        )
    }

    private fun validateRequest(request: LeaveConfirmRequest) {
        val errors = validator.validate(request).map {
            ValidationErrorResponse.FieldErrorDetail(it.propertyPath.toString(), it.message)
        }.sortedWith(compareBy({ it.field }, { it.message }))
        if (errors.isNotEmpty()) {
            throw BusinessException(GlobalErrorCode.VALIDATION_ERROR, ValidationErrorResponse(errors))
        }
    }
}
