package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.ShiftAssignmentReason
import com.merge.backend.domain.shift.entity.ShiftCancellationReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.workplace.dto.request.LeaveConfirmRequest
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 쓰기 없는 V2 계획 검증 부품. 호출자는 먼저 requireLeaveTarget으로 현재 권한·대상 상태를
 * 확인하고, 전체 Lock/Guard 획득 후 같은 Transaction에서 최신 facts와 참조 범위를 전달한다.
 * Ready는 V3 충돌 검사·V4 정리·동시성 보호 완료를 뜻하지 않는다.
 */
@Component
class LeavePlanPreparation(private val contexts: LeavePreviewContextService, private val clock: Clock) {
    fun prepare(
        actorUserId: Long,
        workplaceId: Long,
        memberId: Long,
        request: LeaveConfirmRequest,
        current: LeavePreviewFacts,
        references: References,
        evaluatedAt: Instant = clock.instant()
    ): Result {
        val now = evaluatedAt
        val businessNow = LocalDateTime.ofInstant(now, clock.zone)
        val context = request.previewContext?.takeIf { it.isNotBlank() } ?: return Result.InvalidRequest
        val decisions = request.decisions ?: return Result.InvalidRequest
        if (decisions.any { it == null || it.shiftId == null || it.shiftStartAt == null ||
                it.shiftEndAt == null || it.action !in setOf("REASSIGN", "CANCEL") ||
                (it.action == "REASSIGN" && it.targetMemberId == null)
            }
        ) return Result.InvalidRequest
        if (request.acknowledgedUnavailableConflicts.any { acknowledgement ->
                acknowledgement == null || acknowledgement.shiftId == null || acknowledgement.targetMemberId == null ||
                    acknowledgement.overlaps == null || acknowledgement.overlaps.any {
                        it == null || it.startAt == null || it.endAt == null
                    }
            }
        ) return Result.InvalidRequest
        val original = contexts.verify(context, actorUserId, workplaceId, memberId, now)
            ?: return Result.InvalidContext

        // 진행 중 근무의 종료 시각은 WPL-009 연동용. 시작 경계를 지난 근무도 동일하게 거부한다.
        val ongoingEnd = (current.ongoingShifts + current.publishedShifts)
            .filter { it.startAt <= businessNow && it.endAt > businessNow }
            .maxOfOrNull { it.endAt }
        if (ongoingEnd != null) return Result.OngoingShift(ongoingEnd)

        val selected = decisions.filterNotNull()
        val ids = selected.map { requireNotNull(it.shiftId) }
        if (ids.size != ids.toSet().size) return Result.Stale

        // 원본에 있던 근무의 삭제는 STALE. 무관한 ID의 범위 밖 여부는 404 연동용으로 구분한다.
        val originalIds = original.facts.publishedShifts.map { it.shiftId }.toSet()
        for (id in ids.filter { it !in originalIds }) {
            if (references.shiftWorkplaceIds[id] != workplaceId) return Result.ReferenceNotFound
        }
        if (ids.toSet() != current.publishedShifts.map { it.shiftId }.toSet()) return Result.Stale
        val shifts = current.publishedShifts.associateBy { it.shiftId }
        for (decision in selected) {
            val shift = shifts[decision.shiftId] ?: return Result.Stale
            if (shift.memberId != memberId || shift.scheduleStatus != ScheduleStatus.PUBLISHED ||
                shift.shiftStatus != ShiftStatus.SCHEDULED || shift.startAt <= businessNow ||
                shift.startAt != decision.shiftStartAt || shift.endAt != decision.shiftEndAt
            ) return Result.Stale
            if (decision.action == "REASSIGN") {
                val targetId = requireNotNull(decision.targetMemberId)
                val target = references.members[targetId] ?: return Result.ReferenceNotFound
                if (target.workplaceId != workplaceId) return Result.ReferenceNotFound
                if (targetId == memberId || target.leftAt != null) return Result.Stale
            }
        }
        if (!LeavePreviewComparison.matches(original.facts, current)) return Result.Stale
        return Result.Ready(Plan(
            selected.filter { it.action == "REASSIGN" }.map {
                Reassignment(shifts.getValue(requireNotNull(it.shiftId)), requireNotNull(it.targetMemberId))
            },
            selected.filter { it.action == "CANCEL" }.map { shifts.getValue(requireNotNull(it.shiftId)) },
            current.draftShiftsToDelete.toList(), request.regenerateInviteCode,
            request.acknowledgedUnavailableConflicts.filterNotNull(), current.candidacies.toList()
        ))
    }

    /** Entity의 Lock 대기 전 캐시를 넘기지 않고, 재조회한 최소 사실만 전달한다. */
    data class References(val shiftWorkplaceIds: Map<Long, Long>, val members: Map<Long, Membership>)
    data class Membership(val workplaceId: Long, val leftAt: LocalDateTime?)
    data class Reassignment(val shift: LeavePreviewFacts.WorkShift, val targetMemberId: Long) {
        val reason: ShiftAssignmentReason get() = ShiftAssignmentReason.MEMBER_LEFT
    }
    data class Plan(
        val reassignments: List<Reassignment>,
        val cancellations: List<LeavePreviewFacts.WorkShift>,
        val draftShiftsToDelete: List<LeavePreviewFacts.WorkShift>,
        val regenerateInviteCode: Boolean,
        // 확인 구간은 V3 공통 검증에 전달하며 V2에서 충돌 판단을 복제하지 않는다.
        val acknowledgedUnavailableConflicts: List<LeaveConfirmRequest.AcknowledgedUnavailableConflict>,
        // V4 담당 함수가 실제 잠근 Row로 다시 검사·처리할 대상. V2는 상태를 바꾸지 않는다.
        val candidacies: List<LeavePreviewFacts.Candidacy>
    ) {
        val cancellationReason: ShiftCancellationReason get() = ShiftCancellationReason.MEMBER_LEFT
    }

    // InvalidContext의 400/409, 세부 입력 오류의 응답은 미결정이므로 여기서 HTTP로 매핑하지 않는다.
    sealed interface Result {
        data object InvalidRequest : Result
        data object InvalidContext : Result
        data object ReferenceNotFound : Result
        data object Stale : Result
        data class OngoingShift(val endAt: LocalDateTime) : Result
        data class Ready(val plan: Plan) : Result
    }
}
