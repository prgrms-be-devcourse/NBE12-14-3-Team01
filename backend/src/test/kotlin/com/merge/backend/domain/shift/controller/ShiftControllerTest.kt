package com.merge.backend.domain.shift.controller

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftCancellationReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.dto.PublishedShiftCreateRequest
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.service.PublishedShiftCancellationResult
import com.merge.backend.domain.shift.service.PublishedShiftReassignmentResult
import com.merge.backend.domain.shift.service.ShiftService
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.global.rq.Rq
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.dto.UnavailableConflictOverlap
import com.merge.backend.global.dto.UnavailableConflictResponse
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.GlobalExceptionHandler
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
import org.hamcrest.Matchers.nullValue
import org.springframework.http.MediaType
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
            .setControllerAdvice(GlobalExceptionHandler())
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

    @Test
    fun 공개_Shift_담당자_변경은_200과_변경된_근무_및_종료된_대타_요청_수를_반환한다() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val actorId = 200L
        val targetMemberId = 20L
        val startAt = LocalDateTime.of(2026, 10, 30, 9, 0)
        val endAt = LocalDateTime.of(2026, 10, 30, 18, 0)
        val user = mock<User>()
        val member = mock<WorkplaceMember>()
        val reassignedShift = mock<Shift>()

        given(rq.actorId).willReturn(actorId)
        given(reassignedShift.id).willReturn(shiftId)
        given(reassignedShift.member).willReturn(member)
        given(member.id).willReturn(targetMemberId)
        given(member.user).willReturn(user)
        given(user.name).willReturn("새 담당자")
        given(reassignedShift.startAt).willReturn(startAt)
        given(reassignedShift.endAt).willReturn(endAt)
        given(reassignedShift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shiftService.reassignPublishedShift(
            org.mockito.kotlin.eq(workplaceId),
            org.mockito.kotlin.eq(scheduleId),
            org.mockito.kotlin.eq(shiftId),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(actorId),
        )).willReturn(PublishedShiftReassignmentResult(reassignedShift, 1))

        mockMvc.perform(
            post(
                "/workplaces/{workplaceId}/schedules/{scheduleId}/shifts/{shiftId}/reassign",
                workplaceId,
                scheduleId,
                shiftId,
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"targetMemberId":20,"acknowledgedUnavailableConflicts":[]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.message").value("공개 근무 담당자를 변경했습니다."))
            .andExpect(jsonPath("$.data.shift.shiftId").value(shiftId))
            .andExpect(jsonPath("$.data.shift.memberId").value(targetMemberId))
            .andExpect(jsonPath("$.data.shift.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.closedSubstituteRequests").value(1))

        verify(shiftService).reassignPublishedShift(
            org.mockito.kotlin.eq(workplaceId),
            org.mockito.kotlin.eq(scheduleId),
            org.mockito.kotlin.eq(shiftId),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(actorId),
        )
    }

    @Test
    fun 공개_Shift_추가는_201과_기존_ShiftCreateResponse를_반환한다() {
        val workplaceId = 1L
        val scheduleId = 10L
        val actorId = 200L
        val memberId = 20L
        val shiftId = 100L
        val startAt = LocalDateTime.of(2026, 10, 30, 9, 0)
        val endAt = LocalDateTime.of(2026, 10, 30, 18, 0)
        val user = mock<User>()
        val member = mock<WorkplaceMember>()
        val shift = mock<Shift>()

        given(rq.actorId).willReturn(actorId)
        given(shift.id).willReturn(shiftId)
        given(shift.member).willReturn(member)
        given(member.id).willReturn(memberId)
        given(member.user).willReturn(user)
        given(user.name).willReturn("직원")
        given(shift.startAt).willReturn(startAt)
        given(shift.endAt).willReturn(endAt)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shiftService.createPublishedShift(
            org.mockito.kotlin.eq(workplaceId),
            org.mockito.kotlin.eq(scheduleId),
            org.mockito.kotlin.any<PublishedShiftCreateRequest>(),
            org.mockito.kotlin.eq(actorId),
        )).willReturn(shift)

        mockMvc.perform(
            post(
                "/workplaces/{workplaceId}/schedules/{scheduleId}/published-shifts",
                workplaceId,
                scheduleId,
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"memberId":20,"startAt":"2026-10-30T09:00:00","endAt":"2026-10-30T18:00:00","acknowledgedUnavailableConflicts":[]}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("201"))
            .andExpect(jsonPath("$.message").value("공개 근무가 추가되었습니다."))
            .andExpect(jsonPath("$.data.shiftId").value(shiftId))
            .andExpect(jsonPath("$.data.memberId").value(memberId))
            .andExpect(jsonPath("$.data.status").value("SCHEDULED"))

        verify(shiftService).createPublishedShift(
            org.mockito.kotlin.eq(workplaceId),
            org.mockito.kotlin.eq(scheduleId),
            org.mockito.kotlin.any<PublishedShiftCreateRequest>(),
            org.mockito.kotlin.eq(actorId),
        )
    }

    @Test
    fun 공개_Shift_취소의_시작_후_변경_오류는_SFT020_409_계약을_반환한다() {
        given(rq.actorId).willReturn(200L)
        given(shiftService.cancelPublishedShift(1L, 10L, 100L, 200L))
            .willThrow(BusinessException(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED))

        mockMvc.perform(post("/workplaces/1/schedules/10/shifts/100/cancel"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("SFT-020"))
            .andExpect(jsonPath("$.data").value(nullValue()))
    }

    @Test
    fun 공개_Shift_재배정의_동일_담당자_오류는_SFT022_409_계약을_반환한다() {
        given(rq.actorId).willReturn(200L)
        given(shiftService.reassignPublishedShift(
            org.mockito.kotlin.eq(1L), org.mockito.kotlin.eq(10L), org.mockito.kotlin.eq(100L),
            org.mockito.kotlin.any(), org.mockito.kotlin.eq(200L),
        )).willThrow(BusinessException(ShiftErrorCode.SAME_SHIFT_MEMBER_REASSIGNMENT))

        mockMvc.perform(
            post("/workplaces/1/schedules/10/shifts/100/reassign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"targetMemberId":20,"acknowledgedUnavailableConflicts":[]}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("SFT-022"))
            .andExpect(jsonPath("$.data").value(nullValue()))
    }

    @Test
    fun 공개_Shift_재배정의_불가능_시간_확인_요청은_SFT021_409과_확인_데이터를_반환한다() {
        given(rq.actorId).willReturn(200L)
        val data = ConfirmationRequiredResponse(
            requiresConfirmation = true,
            unavailableConflicts = listOf(
                UnavailableConflictResponse(
                    100L,
                    LocalDateTime.of(2026, 10, 30, 9, 0),
                    LocalDateTime.of(2026, 10, 30, 18, 0),
                    20L,
                    "직원",
                    listOf(UnavailableConflictOverlap(
                        LocalDateTime.of(2026, 10, 30, 10, 0),
                        LocalDateTime.of(2026, 10, 30, 11, 0),
                    )),
                ),
            ),
        )
        given(shiftService.reassignPublishedShift(
            org.mockito.kotlin.eq(1L), org.mockito.kotlin.eq(10L), org.mockito.kotlin.eq(100L),
            org.mockito.kotlin.any(), org.mockito.kotlin.eq(200L),
        )).willThrow(BusinessException(ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT_CONFIRMATION_REQUIRED, data))

        mockMvc.perform(
            post("/workplaces/1/schedules/10/shifts/100/reassign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"targetMemberId":20,"acknowledgedUnavailableConflicts":[]}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SFT-021"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(true))
            .andExpect(jsonPath("$.data.unavailableConflicts[0].shiftId").value(100L))
            .andExpect(jsonPath("$.data.unavailableConflicts[0].overlaps[0].startAt").value("2026-10-30T10:00:00"))
    }

    @Test
    fun 공개_Shift_추가의_동일_스케줄_중복은_SFT009_400_계약을_반환한다() {
        assertPublishedShiftCreateError(ShiftErrorCode.DUPLICATE_LOCAL_SCHEDULE_TIME, "SFT-009")
    }

    @Test
    fun 공개_Shift_추가의_다른_근무지_중복은_SFT010_400_계약을_반환한다() {
        assertPublishedShiftCreateError(ShiftErrorCode.DUPLICATE_GLOBAL_SCHEDULE_TIME, "SFT-010")
    }

    private fun assertPublishedShiftCreateError(errorCode: ShiftErrorCode, code: String) {
        given(rq.actorId).willReturn(200L)
        given(shiftService.createPublishedShift(
            org.mockito.kotlin.eq(1L), org.mockito.kotlin.eq(10L),
            org.mockito.kotlin.any<PublishedShiftCreateRequest>(), org.mockito.kotlin.eq(200L),
        )).willThrow(BusinessException(errorCode))

        mockMvc.perform(
            post("/workplaces/1/schedules/10/published-shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId":20,"startAt":"2026-10-30T09:00:00","endAt":"2026-10-30T18:00:00","acknowledgedUnavailableConflicts":[]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.data").value(nullValue()))
    }
}
