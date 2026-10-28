package com.merge.backend.domain.workplace.controller

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.workplace.dto.response.LeavePreviewResponse
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.service.LeavePreviewFacts
import com.merge.backend.domain.workplace.service.LeavePreviewService
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.GlobalExceptionHandler
import com.merge.backend.global.rq.Rq
import java.time.LocalDateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(LeavePreviewController::class)
@ContextConfiguration(classes = [LeavePreviewController::class, GlobalExceptionHandler::class])
@AutoConfigureMockMvc(addFilters = false)
class LeavePreviewControllerTest {
    @Autowired private lateinit var mvc: MockMvc
    @MockitoBean private lateinit var service: LeavePreviewService
    @MockitoBean private lateinit var rq: Rq

    @Test
    fun serializePreviewWithOngoingShiftAndAllAffectedLists() {
        val now = LocalDateTime.of(2026, 10, 28, 12, 0)
        val future = LeavePreviewFacts.WorkShift(
            3, 20, 40, ScheduleStatus.PUBLISHED, ShiftStatus.SCHEDULED,
            now.toLocalDate(), now.plusDays(1), now.plusDays(1).plusHours(4)
        )
        val ongoing = future.copy(shiftId = 1, startAt = now.minusHours(1), endAt = now.plusHours(1))
        val laterEnding = ongoing.copy(shiftId = 2, endAt = now.plusHours(2))
        val facts = LeavePreviewFacts(
            LeavePreviewFacts.Member(20, "퇴사 대상", WorkplaceRole.EMPLOYEE, now.minusDays(10)),
            listOf(ongoing, laterEnding), listOf(future),
            listOf(future.copy(shiftId = 4, scheduleStatus = ScheduleStatus.DRAFT)),
            listOf(LeavePreviewFacts.ActiveRequest(5, 20, 3, RequestStatus.OPEN, true)),
            listOf(LeavePreviewFacts.Candidacy(
                6, 20, 7, 30, future.copy(shiftId = 8, memberId = 30),
                RequestStatus.ACCEPTED, CandidateStatus.ACCEPTED, 0, false, true
            ))
        )
        whenever(rq.actorId).thenReturn(1)
        whenever(service.getPreview(1, 10, 20)).thenReturn(LeavePreviewResponse.from(facts, "signed-context"))

        mvc.perform(get("/workplaces/10/members/20/leave-preview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.member.memberId").value(20))
            .andExpect(jsonPath("$.data.member.role").value("EMPLOYEE"))
            .andExpect(jsonPath("$.data.canLeave").value(false))
            .andExpect(jsonPath("$.data.blockingReason").value("ONGOING_SHIFT"))
            .andExpect(jsonPath("$.data.ongoingShift.shiftId").value(2))
            .andExpect(jsonPath("$.data.ongoingShift.endAt").value("2026-10-28T14:00:00"))
            .andExpect(jsonPath("$.data.publishedShifts[0].activeRequests[0].status").value("OPEN"))
            .andExpect(jsonPath("$.data.draftShiftsToDelete[0].scheduleStatus").value("DRAFT"))
            .andExpect(jsonPath("$.data.candidacies[0].candidateStatus").value("ACCEPTED"))
            .andExpect(jsonPath("$.data.candidacies[0].wouldBecomeIneligible").value(false))
            .andExpect(jsonPath("$.data.candidacies[0].wouldCloseRequest").value(true))
            .andExpect(jsonPath("$.data.previewContext").value("signed-context"))

        verify(service).getPreview(1, 10, 20)
    }

    @ParameterizedTest
    @EnumSource(value = WorkplaceErrorCode::class, names = [
        "NOT_WORKPLACE_MEMBER", "MANAGER_REQUIRED", "WORKPLACE_MEMBER_NOT_FOUND",
        "MANAGER_CANNOT_LEAVE", "MEMBERSHIP_ALREADY_ENDED"
    ])
    fun preserveErrorEnvelope(code: WorkplaceErrorCode) {
        whenever(rq.actorId).thenReturn(1)
        whenever(service.getPreview(1, 10, 20)).thenThrow(BusinessException(code))

        mvc.perform(get("/workplaces/10/members/20/leave-preview"))
            .andExpect(status().`is`(code.getHttpStatus().value()))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(code.getCode()))
            .andExpect(jsonPath("$.message").value(code.getMessage()))
            .andExpect(jsonPath("$.data").isEmpty)
    }
}
