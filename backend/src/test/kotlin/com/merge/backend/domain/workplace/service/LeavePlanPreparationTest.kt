package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.ShiftAssignmentReason
import com.merge.backend.domain.shift.entity.ShiftCancellationReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.workplace.dto.request.LeaveConfirmRequest
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class LeavePlanPreparationTest {
    private val instant = Instant.parse("2026-10-29T03:00:00Z")
    private val clock = Clock.fixed(instant, ZoneId.of("Asia/Seoul"))
    private val now = LocalDateTime.now(clock)
    private val contexts = LeavePreviewContextService(jacksonObjectMapper(), "leave-plan-test-only-secret")
    private val preparation = LeavePlanPreparation(contexts, clock)
    private val shift = LeavePreviewFacts.WorkShift(30, 20, 40, ScheduleStatus.PUBLISHED,
        ShiftStatus.SCHEDULED, now.toLocalDate().minusDays(3), now.plusDays(1), now.plusDays(1).plusHours(4))
    private val second = shift.copy(shiftId = 31, startAt = now.plusDays(2), endAt = now.plusDays(2).plusHours(4))
    private val draft = shift.copy(shiftId = 32, scheduleStatus = ScheduleStatus.DRAFT)
    private val facts = LeavePreviewFacts(
        LeavePreviewFacts.Member(20, "직원", WorkplaceRole.EMPLOYEE, now.minusMonths(1)),
        emptyList(), listOf(shift, second), listOf(draft), emptyList(), emptyList()
    )
    private val references = LeavePlanPreparation.References(mapOf(30L to 10L, 31L to 10L),
        mapOf(21L to LeavePlanPreparation.Membership(10, null)))
    private val decisions = listOf(
        LeaveConfirmRequest.Decision(30, shift.startAt, shift.endAt, "REASSIGN", 21),
        LeaveConfirmRequest.Decision(31, second.startAt, second.endAt, "CANCEL")
    )

    @Test
    fun preparesHandoverAndCancellationWithMemberLeftReasonsWithoutChangingFacts() {
        val result = prepare(request(regenerate = true)) as LeavePlanPreparation.Result.Ready
        assertThat(result.plan.reassignments.single().targetMemberId).isEqualTo(21)
        assertThat(result.plan.reassignments.single().reason).isEqualTo(ShiftAssignmentReason.MEMBER_LEFT)
        assertThat(result.plan.cancellationReason).isEqualTo(ShiftCancellationReason.MEMBER_LEFT)
        assertThat(result.plan.cancellations).containsExactly(second)
        assertThat(result.plan.draftShiftsToDelete).containsExactly(draft)
        assertThat(result.plan.regenerateInviteCode).isTrue()
        assertThat(facts.publishedShifts).containsExactly(shift, second)
    }

    @Test
    fun rejectsDuplicateMissingAndSameCountReplacedDecisions() {
        for (invalid in listOf(listOf(decisions[0], decisions[0]), listOf(decisions[0]),
            listOf(decisions[0], decisions[1].copy(shiftId = 99)))) {
            val scoped = references.copy(shiftWorkplaceIds = references.shiftWorkplaceIds + (99L to 10L))
            assertThat(prepare(request().copy(decisions = invalid), refs = scoped))
                .isEqualTo(LeavePlanPreparation.Result.Stale)
        }
    }

    @Test
    fun distinguishesMissingOrForeignReferencesFromEndedHandoverTarget() {
        assertThat(prepare(request(), refs = references.copy(members = emptyMap())))
            .isEqualTo(LeavePlanPreparation.Result.ReferenceNotFound)
        assertThat(prepare(request(), refs = references.copy(members = mapOf(21L to
            LeavePlanPreparation.Membership(11, null)))))
            .isEqualTo(LeavePlanPreparation.Result.ReferenceNotFound)
        assertThat(prepare(request(), refs = references.copy(members = mapOf(21L to
            LeavePlanPreparation.Membership(10, now)))))
            .isEqualTo(LeavePlanPreparation.Result.Stale)
        assertThat(prepare(request().copy(decisions = listOf(decisions[0], decisions[1].copy(shiftId = 99)))))
            .isEqualTo(LeavePlanPreparation.Result.ReferenceNotFound)
    }

    @Test
    fun rejectsSelfHandoverAndChangedDecisionTimes() {
        val refs = references.copy(members = references.members + (20L to LeavePlanPreparation.Membership(10, null)))
        assertThat(prepare(request().copy(decisions = listOf(decisions[0].copy(targetMemberId = 20), decisions[1])), refs = refs))
            .isEqualTo(LeavePlanPreparation.Result.Stale)
        assertThat(prepare(request().copy(decisions = listOf(decisions[0].copy(shiftStartAt = now), decisions[1]))))
            .isEqualTo(LeavePlanPreparation.Result.Stale)
    }

    @Test
    fun detectsChangeFromSignedOriginalEvenIfSubmittedTimesMatchCurrentData() {
        val changed = shift.copy(endAt = shift.endAt.plusHours(1))
        assertThat(prepare(request().copy(decisions = listOf(decisions[0].copy(shiftEndAt = changed.endAt), decisions[1])),
            facts.copy(publishedShifts = listOf(changed, second))))
            .isEqualTo(LeavePlanPreparation.Result.Stale)
    }

    @Test
    fun allowsDraftReductionButRejectsNewDraftAndDeletedPublishedShift() {
        assertThat(prepare(request(), facts.copy(draftShiftsToDelete = emptyList())))
            .isInstanceOf(LeavePlanPreparation.Result.Ready::class.java)
        assertThat(prepare(request(), facts.copy(draftShiftsToDelete = listOf(draft.copy(shiftId = 99)))))
            .isEqualTo(LeavePlanPreparation.Result.Stale)
        assertThat(prepare(request(), facts.copy(publishedShifts = listOf(shift))))
            .isEqualTo(LeavePlanPreparation.Result.Stale)
    }

    @Test
    fun rejectsOngoingShiftAtStartBoundaryAndReportsLatestEnd() {
        val ongoing = shift.copy(startAt = now, endAt = now.plusHours(2))
        val current = facts.copy(ongoingShifts = listOf(ongoing, ongoing.copy(shiftId = 99, endAt = now.plusHours(4))))
        assertThat(prepare(request(), current)).isEqualTo(LeavePlanPreparation.Result.OngoingShift(now.plusHours(4)))
        assertThat(prepare(request(), facts.copy(ongoingShifts = listOf(ongoing.copy(endAt = now)))))
            .isInstanceOf(LeavePlanPreparation.Result.Ready::class.java)
    }

    @Test
    fun rejectsInvalidExpiredAndWrongActorContextsWithoutChoosingHttpPolicy() {
        assertThat(prepare(request().copy(previewContext = "invalid")))
            .isEqualTo(LeavePlanPreparation.Result.InvalidContext)
        assertThat(prepare(request().copy(previewContext = contexts.issue(1, 10, 20, instant.minusSeconds(900), facts))))
            .isEqualTo(LeavePlanPreparation.Result.InvalidContext)
        assertThat(prepare(request().copy(previewContext = contexts.issue(2, 10, 20, instant, facts))))
            .isEqualTo(LeavePlanPreparation.Result.InvalidContext)
    }

    @Test
    fun rejectsMalformedDecisionShapeAndAllowsEmptyPlanWithoutFutureShifts() {
        for (invalid in listOf<List<LeaveConfirmRequest.Decision?>?>(null, listOf(null),
            listOf(decisions[0].copy(action = "OTHER")), listOf(decisions[0].copy(targetMemberId = null)),
            listOf(decisions[0].copy(shiftId = null)))) {
            assertThat(prepare(request().copy(decisions = invalid))).isEqualTo(LeavePlanPreparation.Result.InvalidRequest)
        }
        val empty = facts.copy(publishedShifts = emptyList(), draftShiftsToDelete = emptyList())
        assertThat(prepare(LeaveConfirmRequest(contexts.issue(1, 10, 20, instant, empty), emptyList()), empty))
            .isInstanceOf(LeavePlanPreparation.Result.Ready::class.java)
    }

    @Test
    fun carriesAcknowledgementsToV3WithoutDecidingUnconfirmedIntervalPolicy() {
        val acknowledgement = LeaveConfirmRequest.AcknowledgedUnavailableConflict(30, 21,
            listOf(LeaveConfirmRequest.Overlap(shift.startAt, shift.endAt)))
        val result = prepare(request().copy(acknowledgedUnavailableConflicts = listOf(acknowledgement)))
            as LeavePlanPreparation.Result.Ready
        assertThat(result.plan.acknowledgedUnavailableConflicts).containsExactly(acknowledgement)
        assertThat(prepare(request().copy(acknowledgedUnavailableConflicts = listOf(null))))
            .isEqualTo(LeavePlanPreparation.Result.InvalidRequest)
    }

    private fun request(regenerate: Boolean = false) = LeaveConfirmRequest(
        contexts.issue(1, 10, 20, instant, facts), decisions, regenerateInviteCode = regenerate)

    private fun prepare(request: LeaveConfirmRequest, current: LeavePreviewFacts = facts,
        refs: LeavePlanPreparation.References = references) = preparation.prepare(1, 10, 20, request, current, refs)
}
