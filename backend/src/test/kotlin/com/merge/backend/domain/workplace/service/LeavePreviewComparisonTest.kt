package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LeavePreviewComparisonTest {
    private val now = LocalDateTime.of(2026, 10, 28, 12, 0)
    private val shift = LeavePreviewFacts.WorkShift(30, 20, 40, ScheduleStatus.PUBLISHED,
        ShiftStatus.SCHEDULED, LocalDate.of(2026, 10, 26), now.plusDays(1), now.plusDays(1).plusHours(4))
    private val draft = shift.copy(shiftId = 31, scheduleId = 41, scheduleStatus = ScheduleStatus.DRAFT)
    private val request = LeavePreviewFacts.ActiveRequest(50, 20, 30, RequestStatus.OPEN, true)
    private val candidacy = LeavePreviewFacts.Candidacy(60, 20, 51, 21,
        shift.copy(shiftId = 32, memberId = 21), RequestStatus.OPEN, CandidateStatus.PENDING,
        2, true, false)
    private val candidate = LeavePreviewFacts.RequestCandidate(61, 50, 22,
        CandidateStatus.PENDING, WorkplaceRole.EMPLOYEE, null)
    private val original = LeavePreviewFacts(
        LeavePreviewFacts.Member(20, "직원", WorkplaceRole.EMPLOYEE, now.minusMonths(1)),
        emptyList(), listOf(shift), listOf(draft), listOf(request), listOf(candidacy), listOf(candidate)
    )

    @Test
    fun acceptsSameFactsAndDisplayNameChange() {
        assertMatches(original)
        assertMatches(original.copy(member = original.member.copy(name = "새 이름")))
    }

    @Test
    fun rejectsPublishedShiftAdditionRemovalReplacementAndEveryContractChange() {
        val changes = listOf(
            emptyList(), listOf(shift, shift.copy(shiftId = 99)),
            listOf(shift.copy(shiftId = 99)), listOf(shift.copy(memberId = 21)),
            listOf(shift.copy(shiftStatus = ShiftStatus.CANCELLED)),
            listOf(shift.copy(startAt = shift.startAt.plusMinutes(1))),
            listOf(shift.copy(endAt = shift.endAt.plusMinutes(1))),
            listOf(shift.copy(scheduleId = 42)),
            listOf(shift.copy(scheduleStatus = ScheduleStatus.DRAFT))
        )
        changes.forEach { assertStale(original.copy(publishedShifts = it)) }
    }

    @Test
    fun acceptsOnlyUnchangedRemainingDraftDeletionTargets() {
        assertMatches(original.copy(draftShiftsToDelete = emptyList()))
        assertStale(original.copy(draftShiftsToDelete = listOf(draft, draft.copy(shiftId = 99))))
        assertStale(original.copy(draftShiftsToDelete = listOf(draft.copy(shiftId = 99))))
        assertStale(original.copy(draftShiftsToDelete = listOf(draft.copy(memberId = 21))))
        assertStale(original.copy(draftShiftsToDelete = listOf(draft.copy(startAt = now))))
    }

    @Test
    fun rejectsRequestReplacementOrNewAcceptedHistoryButAcceptsClosureElsewhere() {
        assertMatches(original.copy(activeRequestsOnPublishedShifts = emptyList(),
            candidatesOnPublishedShifts = emptyList()))
        assertStale(original.copy(activeRequestsOnPublishedShifts = listOf(request.copy(requestId = 99))))
        assertStale(original.copy(activeRequestsOnPublishedShifts = listOf(request.copy(status = RequestStatus.ACCEPTED))))
        assertStale(original.copy(candidatesOnPublishedShifts = listOf(candidate.copy(candidateStatus = CandidateStatus.ACCEPTED))))
        assertStale(original.copy(candidatesOnPublishedShifts = listOf(candidate.copy(candidateId = 99))))
    }

    @Test
    fun acceptsRemovedCandidacyAndUnchangedEffectsDespitePendingCountChange() {
        assertMatches(original.copy(candidacies = emptyList()))
        assertMatches(original.copy(candidacies = listOf(candidacy.copy(otherCurrentPendingCandidates = 1))))
    }

    @Test
    fun rejectsSameCandidateWithNewRequestClosureOrChangedRelationship() {
        assertStale(original.copy(candidacies = listOf(candidacy.copy(
            otherCurrentPendingCandidates = 0, wouldCloseRequest = true))))
        assertStale(original.copy(candidacies = listOf(candidacy.copy(
            candidateStatus = CandidateStatus.ACCEPTED, requestStatus = RequestStatus.ACCEPTED,
            wouldBecomeIneligible = false, wouldCloseRequest = true))))
        assertStale(original.copy(candidacies = listOf(candidacy.copy(requestId = 99))))
        assertStale(original.copy(candidacies = listOf(candidacy.copy(
            shift = candidacy.shift.copy(endAt = now.plusDays(2))))))
    }

    @Test
    fun acceptsReductionOfRequestClosureEffect() {
        val previous = original.copy(candidacies = listOf(candidacy.copy(
            otherCurrentPendingCandidates = 0, wouldCloseRequest = true)))
        assertThat(LeavePreviewComparison.matches(previous, original)).isTrue()
    }

    @Test
    fun acceptsOtherCandidateLosingMembershipButRejectsRestoredEligibility() {
        val ended = original.copy(candidatesOnPublishedShifts = listOf(candidate.copy(memberLeftAt = now)))
        assertMatches(ended)
        assertThat(LeavePreviewComparison.matches(ended, original)).isFalse()
    }

    @Test
    fun rejectsDifferentMembershipIdentityRoleOrJoinedAt() {
        assertStale(original.copy(member = original.member.copy(memberId = 99)))
        assertStale(original.copy(member = original.member.copy(role = WorkplaceRole.MANAGER)))
        assertStale(original.copy(member = original.member.copy(joinedAt = now)))
    }

    @Test
    fun comparesPublishedShiftsIndependentlyOfQueryOrder() {
        val another = shift.copy(shiftId = 99, startAt = now.plusDays(2), endAt = now.plusDays(2).plusHours(4))
        assertThat(LeavePreviewComparison.matches(
            original.copy(publishedShifts = listOf(shift, another)),
            original.copy(publishedShifts = listOf(another, shift))
        )).isTrue()
    }

    private fun assertMatches(current: LeavePreviewFacts) {
        assertThat(LeavePreviewComparison.matches(original, current)).isTrue()
    }

    private fun assertStale(current: LeavePreviewFacts) {
        assertThat(LeavePreviewComparison.matches(original, current)).isFalse()
    }
}
