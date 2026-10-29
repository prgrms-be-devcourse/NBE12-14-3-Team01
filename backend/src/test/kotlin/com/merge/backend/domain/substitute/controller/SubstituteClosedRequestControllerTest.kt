package com.merge.backend.domain.substitute.controller

import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.substitute.dto.ClosedSubstituteRequestPageResponse
import com.merge.backend.domain.substitute.dto.ClosedSubstituteRequestResponse
import com.merge.backend.domain.substitute.dto.ClosedSubstituteRequestResponse.Outcome
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.service.SubstituteCandidateService
import com.merge.backend.domain.substitute.service.SubstituteRequestService
import com.merge.backend.global.exception.GlobalExceptionHandler
import com.merge.backend.global.rq.Rq
import java.time.LocalDateTime
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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

// API-SB-02 HTTP 부분: Query 파라미터 전달과 응답 JSON (V4-N-51·53). 조회 규칙은 SubstituteClosedRequestTest
@WebMvcTest(SubstituteRequestController::class)
@ContextConfiguration(classes = [SubstituteRequestController::class, GlobalExceptionHandler::class])
@AutoConfigureMockMvc(addFilters = false)
class SubstituteClosedRequestControllerTest {
    @Autowired private lateinit var mvc: MockMvc
    @MockitoBean private lateinit var substituteRequestService: SubstituteRequestService
    @MockitoBean private lateinit var substituteCandidateService: SubstituteCandidateService
    @MockitoBean private lateinit var rq: Rq

    @Test
    @DisplayName("파라미터가 없으면 기간은 비워서(Service가 최근 7일로) page 0, size 20으로 넘기고 정해진 JSON Field만 준다")
    fun defaultParametersAndJsonFields() {
        // given
        val startAt = LocalDateTime.of(2026, 10, 30, 10, 0)
        val item = ClosedSubstituteRequestResponse(
            requestId = 5, shiftId = 3, startAt = startAt, endAt = startAt.plusHours(4),
            shiftStatus = ShiftStatus.SCHEDULED, outcome = Outcome.CLOSED,
            closeReason = RequestCloseReason.ALL_CANDIDATES_REJECTED, closedAt = startAt.minusDays(1),
            requesterName = "요청자", requesterMembershipEnded = false, accepterName = null,
            requesterStillAssigned = true, shiftStarted = false, hasNewerActiveRequest = false,
        )
        whenever(rq.actorId).thenReturn(9)
        whenever(substituteRequestService.findClosedRequests(1, 9, null, null, 0, 20))
            .thenReturn(ClosedSubstituteRequestPageResponse(listOf(item), 0, 20, false))

        // when & then
        mvc.perform(get("/workplaces/1/substitute-requests/closed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.items[0].requestId").value(5))
            .andExpect(jsonPath("$.data.items[0].shiftId").value(3))
            .andExpect(jsonPath("$.data.items[0].startAt").value("2026-10-30T10:00:00"))
            .andExpect(jsonPath("$.data.items[0].shiftStatus").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.items[0].outcome").value("CLOSED"))
            .andExpect(jsonPath("$.data.items[0].closeReason").value("ALL_CANDIDATES_REJECTED"))
            .andExpect(jsonPath("$.data.items[0].closedAt").value("2026-10-29T10:00:00"))
            .andExpect(jsonPath("$.data.items[0].requesterName").value("요청자"))
            .andExpect(jsonPath("$.data.items[0].requesterMembershipEnded").value(false))
            .andExpect(jsonPath("$.data.items[0].accepterName").isEmpty)
            .andExpect(jsonPath("$.data.items[0].requesterStillAssigned").value(true))
            .andExpect(jsonPath("$.data.items[0].shiftStarted").value(false))
            .andExpect(jsonPath("$.data.items[0].hasNewerActiveRequest").value(false))
            .andExpect(jsonPath("$.data.items[0].requesterMemberId").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].workplaceId").doesNotExist())

        verify(substituteRequestService).findClosedRequests(1, 9, null, null, 0, 20)
    }

    @Test
    @DisplayName("from·to는 ISO 날짜시각으로 받고 page·size를 그대로 넘긴다")
    fun passesIsoPeriodAndPage() {
        // given
        val from = LocalDateTime.of(2026, 10, 1, 0, 0)
        val to = LocalDateTime.of(2026, 10, 29, 23, 59, 59)
        whenever(rq.actorId).thenReturn(9)
        whenever(substituteRequestService.findClosedRequests(1, 9, from, to, 2, 50))
            .thenReturn(ClosedSubstituteRequestPageResponse(emptyList(), 2, 50, false))

        // when & then
        mvc.perform(
            get("/workplaces/1/substitute-requests/closed")
                .param("from", "2026-10-01T00:00:00")
                .param("to", "2026-10-29T23:59:59")
                .param("page", "2")
                .param("size", "50"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items").isEmpty)
            .andExpect(jsonPath("$.data.page").value(2))

        verify(substituteRequestService).findClosedRequests(1, 9, from, to, 2, 50)
    }

    @Test
    @DisplayName("날짜시각·숫자 형식이 틀리면 400 COM-003이고 Service를 부르지 않는다")
    fun malformedParametersAreRejected() {
        whenever(rq.actorId).thenReturn(9)

        listOf("from" to "2026-10-01", "to" to "abc", "page" to "x", "size" to "1.5").forEach { (name, value) ->
            mvc.perform(get("/workplaces/1/substitute-requests/closed").param(name, value))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COM-003"))
        }

        verifyNoInteractions(substituteRequestService)
    }
}
