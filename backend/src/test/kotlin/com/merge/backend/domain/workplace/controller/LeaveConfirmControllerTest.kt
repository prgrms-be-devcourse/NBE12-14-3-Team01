package com.merge.backend.domain.workplace.controller

import com.merge.backend.domain.workplace.dto.response.LeaveConfirmResponse
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.service.LeaveConfirmService
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.dto.UnavailableConflictOverlap
import com.merge.backend.global.dto.UnavailableConflictResponse
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.GlobalExceptionHandler
import com.merge.backend.global.rq.Rq
import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** Controller 단위 HTTP 계약. Readiness Bean을 등록하거나 실제 POST를 활성화하지 않는다. */
class LeaveConfirmControllerTest {
    private val service = mock<LeaveConfirmService>()
    private val rq = mock<Rq>()
    private lateinit var mvc: MockMvc
    private val now = LocalDateTime.of(2026, 10, 29, 12, 0, 47)
    private val validBody = """{"previewContext":"test-context","decisions":[]}"""

    @BeforeEach
    fun setUp() {
        whenever(rq.actorId).thenReturn(1)
        mvc = MockMvcBuilders.standaloneSetup(LeaveConfirmController(service, rq))
            .setControllerAdvice(GlobalExceptionHandler()).build()
    }

    @Test
    fun serializesResultAndOmitsInviteCodeUnlessRotated() {
        val response = LeaveConfirmResponse(now, emptyList(), emptyList(), emptyList(),
            LeaveConfirmResponse.ClosedRequests.from(setOf(1), setOf(2)), 1, null)
        whenever(service.confirm(eq(1), eq(10), eq(20), any())).thenReturn(response)
        mvc.perform(post("/workplaces/10/members/20/leave").contentType(MediaType.APPLICATION_JSON).content(validBody))
            .andExpect(status().isOk).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.leftAt").value("2026-10-29T12:00:47"))
            .andExpect(jsonPath("$.data.closedRequests.total").value(2))
            .andExpect(jsonPath("$.data.closedRequests.byReason.SHIFT_CHANGED").value(1))
            .andExpect(jsonPath("$.data.closedRequests.byReason.CANDIDATE_INELIGIBLE").value(1))
            .andExpect(jsonPath("$.data.ineligibleCandidacies").value(1))
            .andExpect(jsonPath("$.data.newInviteCode").doesNotExist())
        whenever(service.confirm(eq(1), eq(10), eq(20), any())).thenReturn(response.copy(newInviteCode = "A1B2C3D4"))
        mvc.perform(post("/workplaces/10/members/20/leave").contentType(MediaType.APPLICATION_JSON).content(validBody))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.newInviteCode").value("A1B2C3D4"))
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    fun rejectsMissingHandoverTargetAndNullElementsBeforeCallingService(body: String) {
        mvc.perform(post("/workplaces/10/members/20/leave").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.data.fieldErrors").isNotEmpty)
        verifyNoInteractions(service)
    }

    @ParameterizedTest
    @EnumSource(value = WorkplaceErrorCode::class, names = ["NOT_WORKPLACE_MEMBER", "MANAGER_REQUIRED",
        "WORKPLACE_MEMBER_NOT_FOUND", "MANAGER_CANNOT_LEAVE", "MEMBERSHIP_ALREADY_ENDED",
        "ONGOING_SHIFT_PREVENTS_LEAVE", "STALE_LEAVE_PLAN", "LEAVE_UNAVAILABLE_CONFLICT_CONFIRMATION_REQUIRED"])
    fun preservesExistingErrorStatusAndEnvelope(code: WorkplaceErrorCode) {
        whenever(service.confirm(eq(1), eq(10), eq(20), any())).thenThrow(BusinessException(code))
        mvc.perform(post("/workplaces/10/members/20/leave").contentType(MediaType.APPLICATION_JSON).content(validBody))
            .andExpect(status().`is`(code.getHttpStatus().value()))
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.code").value(code.getCode()))
            .andExpect(jsonPath("$.message").value(code.getMessage()))
    }

    @Test
    fun preservesOngoingShiftEndAndConfirmationIntervalsInDistinct409Responses() {
        whenever(service.confirm(eq(1), eq(10), eq(20), any())).thenThrow(BusinessException(
            WorkplaceErrorCode.ONGOING_SHIFT_PREVENTS_LEAVE, mapOf("endAt" to now)))
        mvc.perform(post("/workplaces/10/members/20/leave").contentType(MediaType.APPLICATION_JSON).content(validBody))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("WPL-009"))
            .andExpect(jsonPath("$.data.endAt").value("2026-10-29T12:00:47"))
        val warning = ConfirmationRequiredResponse(true, listOf(UnavailableConflictResponse(
            30, now, now.plusHours(4), 21, "인계 대상", listOf(UnavailableConflictOverlap(now, now.plusHours(1))))))
        whenever(service.confirm(eq(1), eq(10), eq(20), any())).thenThrow(BusinessException(
            WorkplaceErrorCode.LEAVE_UNAVAILABLE_CONFLICT_CONFIRMATION_REQUIRED, warning))
        mvc.perform(post("/workplaces/10/members/20/leave").contentType(MediaType.APPLICATION_JSON).content(validBody))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("WPL-011"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(true))
            .andExpect(jsonPath("$.data.unavailableConflicts[0].targetMemberId").value(21))
            .andExpect(jsonPath("$.data.unavailableConflicts[0].overlaps[0].startAt").value("2026-10-29T12:00:47"))
            .andExpect(jsonPath("$.data.unavailableConflicts[0].unavailableTimeId").doesNotExist())
    }

    companion object {
        @JvmStatic
        fun invalidBodies(): List<String> = listOf(
            """{"decisions":[]}""",
            """{"previewContext":"test-context","decisions":[null]}""",
            """{"previewContext":"test-context","decisions":[{"shiftId":30,"shiftStartAt":"2026-10-30T09:00:00","shiftEndAt":"2026-10-30T13:00:00","action":"REASSIGN"}]}""",
            """{"previewContext":"test-context","decisions":[],"acknowledgedUnavailableConflicts":[null]}""",
            """{"previewContext":"test-context","decisions":[],"acknowledgedUnavailableConflicts":[{"shiftId":30,"targetMemberId":21,"overlaps":[null]}]}"""
        )
    }
}
