package com.merge.backend.domain.shift.controller

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftCancellationReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.service.PublishedShiftCancellationResult
import com.merge.backend.domain.shift.service.ShiftService
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.global.rq.Rq
import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class ShiftControllerTest {

    @Mock
    private lateinit var shiftService: ShiftService

    @Mock
    private lateinit var rq: Rq

    @InjectMocks
    private lateinit var shiftController: ShiftController

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(shiftController)
            .build()
    }

    @Test
    fun 공개_Shift_취소는_200과_취소된_근무_및_종료된_대타_요청_수를_반환한다() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val actorId = 200L
        val startAt = LocalDateTime.of(2026, 10, 30, 9, 0)
        val endAt = LocalDateTime.of(2026, 10, 30, 18, 0)
        val cancelledAt = LocalDateTime.of(2026, 10, 28, 15, 0)
        val user = mock<User>()
        val member = mock<WorkplaceMember>()
        val cancelledShift = mock<Shift>()

        given(rq.actorId).willReturn(actorId)
        given(cancelledShift.id).willReturn(shiftId)
        given(cancelledShift.member).willReturn(member)
        given(member.id).willReturn(20L)
        given(member.user).willReturn(user)
        given(user.name).willReturn("직원")
        given(cancelledShift.startAt).willReturn(startAt)
        given(cancelledShift.endAt).willReturn(endAt)
        given(cancelledShift.status).willReturn(ShiftStatus.CANCELLED)
        given(cancelledShift.cancellationReason).willReturn(ShiftCancellationReason.MANAGER_CANCELLED)
        given(cancelledShift.cancelledAt).willReturn(cancelledAt)
        given(shiftService.cancelPublishedShift(workplaceId, scheduleId, shiftId, actorId))
            .willReturn(PublishedShiftCancellationResult(cancelledShift, 2))

        mockMvc.perform(
            post(
                "/workplaces/{workplaceId}/schedules/{scheduleId}/shifts/{shiftId}/cancel",
                workplaceId,
                scheduleId,
                shiftId,
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.message").value("공개 근무를 취소했습니다."))
            .andExpect(jsonPath("$.data.shift.shiftId").value(shiftId))
            .andExpect(jsonPath("$.data.shift.memberId").value(20L))
            .andExpect(jsonPath("$.data.shift.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.shift.cancellationReason").value("MANAGER_CANCELLED"))
            .andExpect(jsonPath("$.data.shift.cancelledAt").value("2026-10-28T15:00:00"))
            .andExpect(jsonPath("$.data.closedSubstituteRequests").value(2))

        verify(shiftService).cancelPublishedShift(workplaceId, scheduleId, shiftId, actorId)
    }
}
