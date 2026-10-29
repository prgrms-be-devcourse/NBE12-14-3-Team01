package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.dto.ShiftRequest
import com.merge.backend.domain.shift.dto.ShiftReassignRequest
import com.merge.backend.domain.shift.dto.PublishedShiftCreateRequest
import com.merge.backend.domain.shift.dto.AcknowledgedNewShiftUnavailableConflict
import com.merge.backend.domain.shift.dto.NewShiftKey
import com.merge.backend.domain.shift.dto.PublishedShiftConfirmationRequiredResponse
import com.merge.backend.domain.shift.dto.AcknowledgedUnavailableConflict
import com.merge.backend.domain.shift.dto.UnavailableOverlap
import com.merge.backend.domain.shift.entity.ShiftAssignmentHistory
import com.merge.backend.domain.shift.entity.ShiftAssignmentReason
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftCancellationReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftAssignmentHistoryRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.substitute.service.ShiftChangeClosedRequest
import com.merge.backend.domain.substitute.service.SubstituteRequestService
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceMemberScope
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.domain.workplace.service.WorkplaceMemberService
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.exception.BusinessException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.BeforeEach

@ExtendWith(MockitoExtension::class)
class ShiftServiceTest {
    @InjectMocks
    private lateinit var shiftService: ShiftService

    @Mock
    private lateinit var shiftRepository: ShiftRepository

    @Mock
    private lateinit var scheduleRepository: ScheduleRepository

    @Mock
    private lateinit var workplaceRepository: WorkplaceRepository
    @Mock
    private lateinit var workplaceMemberService: WorkplaceMemberService
    @Mock
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository
    @Mock
    private lateinit var unavailableTimeRepository: UnavailableTimeRepository
    @Mock
    private lateinit var substituteRequestService: SubstituteRequestService
    @Mock
    private lateinit var shiftAssignmentHistoryRepository: ShiftAssignmentHistoryRepository
    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var clock: Clock

    // 공개 후 변경 알림 Event 발행용 (알림 저장은 별도 통합 Test에서 확인)
    @Mock
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    // 소속 Guard(FOR SHARE 재확인)는 Mock에서 기본으로 "현재 소속"으로 둔다. 실제 Lock 동작은 DraftMembershipGuardConcurrencyTest
    @BeforeEach
    fun allowCurrentMembership() {
        Mockito.lenient().`when`(workplaceMemberRepository.existsCurrentMember(any(), any())).thenReturn(true)
    }

    @Test
    @DisplayName("SCH-01 - Pattern에서 전달된 정보로 SCHEDULED Shift를 저장한다")
    fun createFromValidatedPattern_SavesScheduledShift() {
        val schedule = mock<Schedule>()
        val member = mock<WorkplaceMember>()
        val startAt = LocalDateTime.of(2026, 9, 23, 9, 0)
        val endAt = LocalDateTime.of(2026, 9, 23, 14, 0)
        val savedShift = mock<Shift>()
        given(shiftRepository.save(any<Shift>())).willReturn(savedShift)
        val result = shiftService.createFromValidatedPattern(schedule, member, startAt, endAt)
        val captor = argumentCaptor<Shift>()
        verify(shiftRepository).save(captor.capture())
        assertThat(captor.firstValue.schedule).isSameAs(schedule)
        assertThat(captor.firstValue.member).isSameAs(member)
        assertThat(captor.firstValue.startAt).isEqualTo(startAt)
        assertThat(captor.firstValue.endAt).isEqualTo(endAt)
        assertThat(captor.firstValue.status).isEqualTo(ShiftStatus.SCHEDULED)
        assertThat(result).isSameAs(savedShift)
    }

    @Test
    @DisplayName("근무 상세 조회 성공 - 본인의 확정된 근무 정보 반환")
    fun detail_Success() {
        val shiftId = 1L
        val currentUserId = 100L
        val user = mock<User>()
        val member = mock<WorkplaceMember>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        given(user.id).willReturn(currentUserId)
        given(member.user).willReturn(user)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.member).willReturn(member)
        given(shiftRepository.findById(shiftId)).willReturn(Optional.of(shift))
        val result = shiftService.detail(shiftId, currentUserId)
        assertThat(result).isNotNull()
        assertThat(result).isEqualTo(shift)
    }

    @Test
    @DisplayName("근무 상세 조회 실패 - 존재하지 않는 근무 ID인 경우")
    fun detail_Fail_NotFoundShift() {
        given(shiftRepository.findById(999L)).willReturn(Optional.empty())
        assertThatThrownBy { shiftService.detail(999L, 100L) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(ShiftErrorCode.NOT_FOUND_SHIFT_ERROR.getMessage())
    }

    @Test
    @DisplayName("근무 상세 조회 실패 - 스케줄이 PUBLISHED 상태가 아닌 경우")
    fun detail_Fail_NotPublishedSchedule() {
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(shift.schedule).willReturn(schedule)
        given(shiftRepository.findById(1L)).willReturn(Optional.of(shift))
        assertThatThrownBy { shiftService.detail(1L, 100L) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(ShiftErrorCode.NOT_PUBLISHED_SHIFT.getMessage())
    }

    @Test
    @DisplayName("근무 상세 조회 실패 - 취소된 근무인 경우")
    fun detail_Fail_CanceledShift() {
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.CANCELLED)
        given(shiftRepository.findById(1L)).willReturn(Optional.of(shift))
        assertThatThrownBy { shiftService.detail(1L, 100L) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(ShiftErrorCode.IS_CANCELED_SHIFT.getMessage())
    }

    @Test
    @DisplayName("근무 상세 조회 실패 - 다른 사용자의 근무를 조회하려는 경우")
    fun detail_Fail_ForbiddenAccess() {
        val user = mock<User>()
        val member = mock<WorkplaceMember>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        given(user.id).willReturn(200L)
        given(member.user).willReturn(user)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.member).willReturn(member)
        given(shiftRepository.findById(1L)).willReturn(Optional.of(shift))
        assertThatThrownBy { shiftService.detail(1L, 100L) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(ShiftErrorCode.FORBIDDEN_ACCESS.getMessage())
    }

    @Test
    @DisplayName("성공 - DRAFT 상태 스케줄의 근무를 정상적으로 삭제한다.")
    fun delete_success() {
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val managerMember = mock<WorkplaceMember>()
        given(workplaceRepository.findById(1L)).willReturn(Optional.of(workplace))
        given(workplaceMemberService.requireManager(200L, 1L)).willReturn(managerMember)
        given(scheduleRepository.findByIdForUpdate(10L)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(1L)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(shiftRepository.findById(100L)).willReturn(Optional.of(shift))
        given(shift.schedule).willReturn(schedule)
        given(schedule.id).willReturn(10L)
        shiftService.delete(1L, 10L, 100L, 200L)
        verify(shiftRepository, times(1)).deleteById(100L)
    }

    @Test
    @DisplayName("실패 - 매니저 권한이 없으면 FORBIDDEN_ACCESS 예외가 발생한다.")
    fun delete_forbidden_whenNotManager() {
        doThrow(BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED))
            .whenever(workplaceMemberService)
            .requireManager(200L, 1L)
        assertThrows<BusinessException> {
            shiftService.delete(1L, 10L, 100L, 200L)
        }
        verify(shiftRepository, never()).deleteById(any())
    }
    @Test
    @DisplayName("실패 - 스케줄 상태가 DRAFT가 아니면 INVALID_STATUS_VALUE 예외가 발생한다.")
    fun delete_fail_whenScheduleNotDraft() {
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val managerMember = mock<WorkplaceMember>()
        given(workplaceRepository.findById(1L)).willReturn(Optional.of(workplace))
        given(workplaceMemberService.requireManager(200L, 1L)).willReturn(managerMember)
        given(scheduleRepository.findByIdForUpdate(10L)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(1L)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        val exception = assertThrows<BusinessException> { shiftService.delete(1L, 10L, 100L, 200L) }
        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.INVALID_STATUS_VALUE)
        verify(shiftRepository, never()).deleteById(any())
    }

    @Test
    @DisplayName("실패 - 삭제하려는 근무가 해당 스케줄 소속이 아니면 INVALID_SHIFT_VALUE 예외가 발생한다.")
    fun delete_fail_whenShiftNotInSchedule() {
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val otherSchedule = mock<Schedule>()
        val shift = mock<Shift>()
        val managerMember = mock<WorkplaceMember>()
        given(workplaceRepository.findById(1L)).willReturn(Optional.of(workplace))
        given(workplaceMemberService.requireManager(200L, 1L)).willReturn(managerMember)
        given(scheduleRepository.findByIdForUpdate(10L)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(1L)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(shiftRepository.findById(100L)).willReturn(Optional.of(shift))
        given(shift.schedule).willReturn(otherSchedule)
        given(otherSchedule.id).willReturn(99L)
        val exception = assertThrows<BusinessException> { shiftService.delete(1L, 10L, 100L, 200L) }
        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.INVALID_SHIFT_VALUE)
        verify(shiftRepository, never()).deleteById(any())
    }

    @Test
    @DisplayName("주 시작 일자와 사용자 ID로 주간 근무 목록을 성공적으로 조회한다")
    fun list_Success() {
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val currentUserId = 1L
        val mockShift1 = mock<Shift>()
        val mockShift2 = mock<Shift>()
        val expectedShifts = listOf(mockShift1, mockShift2)
        given(shiftRepository.findAllByWeekStartDateAndCurrentUserId(weekStartDate, currentUserId)).willReturn(expectedShifts)
        val result = shiftService.list(weekStartDate, currentUserId)
        assertThat(result).hasSize(2)
        assertThat(result).isEqualTo(expectedShifts)
        verify(shiftRepository).findAllByWeekStartDateAndCurrentUserId(weekStartDate, currentUserId)
    }

    @Test
    @DisplayName("월요일이 아닌 날짜로 주간 근무를 조회하면 실패한다")
    fun list_Fail_WhenWeekStartDateIsNotMonday() {
        val weekStartDate = LocalDate.of(2026, 9, 22)
        val currentUserId = 1L
        val exception = assertThrows<BusinessException> { shiftService.list(weekStartDate, currentUserId) }
        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.INVALID_INPUT_VALUE)
        verifyNoInteractions(shiftRepository)
    }

    @Test
    @DisplayName("SCH-04 - UnavailableTime 충돌 미확인 시 확인 필요 Warning을 반환한다")
    fun createThrowsWarningWhenUnavailableConflictIsNotConfirmed() {
        val actorId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val memberId = 30L
        val userId = 40L
        val startAt = LocalDateTime.of(2026, 9, 21, 10, 0)
        val endAt = LocalDateTime.of(2026, 9, 21, 14, 0)
        val reqBody = mock<ShiftRequest>()
        val schedule = mock<Schedule>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()
        val user = mock<User>()
        given(reqBody.memberId).willReturn(memberId)
        given(reqBody.startAt).willReturn(startAt)
        given(reqBody.endAt).willReturn(endAt)
        given(reqBody.confirmUnavailableConflict).willReturn(false)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(schedule.weekStartDate).willReturn(LocalDate.of(2026, 9, 21))
        given(workplace.id).willReturn(workplaceId)
        given(workplaceMemberRepository.findById(memberId)).willReturn(Optional.of(member))
        given(member.workplace).willReturn(workplace)
        given(member.leftAt).willReturn(null)
        given(member.id).willReturn(memberId)
        given(member.user).willReturn(user)
        given(user.id).willReturn(userId)
        given(unavailableTimeRepository.existsOverlappingUnavailableTime(userId, startAt, endAt)).willReturn(true)
        val exception = assertThrows<BusinessException> {
            shiftService.create(reqBody, workplaceId, scheduleId, actorId)
        }
        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT)
        assertThat(exception.data).isInstanceOf(ConfirmationRequiredResponse::class.java)
        assertThat((exception.data as ConfirmationRequiredResponse).requiresConfirmation).isTrue()
        verify(scheduleRepository).findByIdForUpdate(scheduleId)
        verify(shiftRepository, never()).save(any())
    }
    @Test
    @DisplayName("SCH-02 - UnavailableTime 충돌 미확인 시 확인 필요 Warning을 반환한다")
    fun modifyThrowsWarningWhenUnavailableConflictIsNotConfirmed() {
        val actorId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val shiftId = 30L
        val memberId = 40L
        val userId = 50L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val startAt = LocalDateTime.of(2026, 9, 21, 10, 0)
        val endAt = LocalDateTime.of(2026, 9, 21, 14, 0)
        val reqBody = mock<ShiftRequest>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()
        val user = mock<User>()
        given(reqBody.memberId).willReturn(memberId)
        given(reqBody.startAt).willReturn(startAt)
        given(reqBody.endAt).willReturn(endAt)
        given(reqBody.confirmUnavailableConflict).willReturn(false)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(schedule.weekStartDate).willReturn(weekStartDate)
        given(shiftRepository.findById(shiftId)).willReturn(Optional.of(shift))
        given(shift.schedule).willReturn(schedule)
        given(schedule.id).willReturn(scheduleId)
        given(shift.id).willReturn(shiftId)
        given(workplaceMemberRepository.findById(memberId)).willReturn(Optional.of(member))
        given(member.workplace).willReturn(workplace)
        given(member.leftAt).willReturn(null)
        given(member.id).willReturn(memberId)
        given(member.user).willReturn(user)
        given(user.id).willReturn(userId)
        given(unavailableTimeRepository.existsOverlappingUnavailableTime(userId, startAt, endAt)).willReturn(true)
        val exception = assertThrows<BusinessException> { shiftService.modify(workplaceId, scheduleId, shiftId, reqBody, actorId) }
        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT)
        assertThat(exception.data).isInstanceOf(ConfirmationRequiredResponse::class.java)
        assertThat((exception.data as ConfirmationRequiredResponse).requiresConfirmation).isTrue()
        verify(unavailableTimeRepository).existsOverlappingUnavailableTime(userId, startAt, endAt)
        verify(scheduleRepository).findByIdForUpdate(scheduleId)
        verify(shift, never()).update(any(), any(), any())
    }

    @Test
    @DisplayName("공개된 시작 전 Shift를 취소하고 취소 메타데이터를 기록한다")
    fun cancelPublishedShift_Success() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(now.plusHours(1))
        given(shift.id).willReturn(shiftId)
        given(substituteRequestService.closeActiveRequestsByShiftChange(shiftId, now)).willReturn(
            listOf(
                ShiftChangeClosedRequest(1L, 10L, null),
                ShiftChangeClosedRequest(2L, 11L, 12L),
            ),
        )

        val result = shiftService.cancelPublishedShift(workplaceId, scheduleId, shiftId, actorId)

        assertThat(result.shift).isSameAs(shift)
        assertThat(result.closedSubstituteRequests).isEqualTo(2)
        verify(shift).cancel(
            eq(ShiftCancellationReason.MANAGER_CANCELLED),
            eq(now),
            eq(actorMember),
        )
        inOrder(scheduleRepository, shiftRepository, substituteRequestService) {
            verify(scheduleRepository).findByIdForUpdate(scheduleId)
            verify(shiftRepository).findByIdForUpdate(shiftId)
            verify(substituteRequestService).closeActiveRequestsByShiftChange(shiftId, now)
        }
    }

    @Test
    @DisplayName("대타 요청 종료에 실패하면 공개 Shift를 취소하지 않는다")
    fun cancelPublishedShift_DoesNotCancelWhenClosingSubstituteRequestsFails() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.id).willReturn(shiftId)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(now.plusHours(1))
        doThrow(IllegalStateException("V4 종료 실패"))
            .whenever(substituteRequestService)
            .closeActiveRequestsByShiftChange(shiftId, now)

        assertThatThrownBy {
            shiftService.cancelPublishedShift(workplaceId, scheduleId, shiftId, actorId)
        }.isInstanceOf(IllegalStateException::class.java)

        verify(shift, never()).cancel(any(), any(), any())
    }

    @Test
    @DisplayName("관리자가 아니면 공개 Shift를 취소할 수 없고 잠금을 획득하지 않는다")
    fun cancelPublishedShift_FailsWhenActorIsNotManager() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val actorId = 200L

        doThrow(BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED))
            .whenever(workplaceMemberService)
            .requireManager(actorId, workplaceId)

        val exception = assertThrows<BusinessException> {
            shiftService.cancelPublishedShift(workplaceId, scheduleId, shiftId, actorId)
        }

        assertThat(exception.errorCode).isEqualTo(WorkplaceErrorCode.MANAGER_REQUIRED)
        verifyNoInteractions(scheduleRepository, shiftRepository)
    }

    @Test
    @DisplayName("시작한 공개 Shift는 취소할 수 없다")
    fun cancelPublishedShift_FailsWhenShiftStarted() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(now)

        val exception = assertThrows<BusinessException> {
            shiftService.cancelPublishedShift(workplaceId, scheduleId, shiftId, actorId)
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        verify(shift, never()).cancel(any(), any(), any())
    }

    @Test
    @DisplayName("이미 취소된 공개 Shift는 다시 취소할 수 없다")
    fun cancelPublishedShift_FailsWhenShiftAlreadyCancelled() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val actorId = 200L
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()

        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.CANCELLED)

        val exception = assertThrows<BusinessException> {
            shiftService.cancelPublishedShift(workplaceId, scheduleId, shiftId, actorId)
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        verify(shift, never()).cancel(any(), any(), any())
    }

    @Test
    @DisplayName("공개되지 않은 근무표의 Shift는 취소할 수 없다")
    fun cancelPublishedShift_FailsWhenScheduleIsNotPublished() {
        val workplaceId = 1L
        val scheduleId = 10L
        val actorId = 200L
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val actorMember = mock<WorkplaceMember>()

        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)

        val exception = assertThrows<BusinessException> {
            shiftService.cancelPublishedShift(workplaceId, scheduleId, 100L, actorId)
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        verifyNoInteractions(shiftRepository)
    }

    @Test
    @DisplayName("공개 Shift 담당자 변경은 활성 대타를 종료하고 이력을 같은 순서로 저장한다")
    fun reassignPublishedShift_ReassignsAndSavesHistory() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val targetMemberId = 20L
        val targetUserId = 30L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()
        val previousMember = mock<WorkplaceMember>()
        val targetMember = mock<WorkplaceMember>()
        val targetUser = mock<User>()
        val targetScope = mock<WorkplaceMemberScope>()
        val unavailableTime = mock<com.merge.backend.domain.shift.entity.UnavailableTime>()
        val request = ShiftReassignRequest(
            targetMemberId,
            listOf(
                AcknowledgedUnavailableConflict(
                    shiftId,
                    targetMemberId,
                    listOf(
                        UnavailableOverlap(now.plusHours(2), now.plusHours(2).plusMinutes(30)),
                        UnavailableOverlap(now.plusHours(2).plusMinutes(30), now.plusHours(3)),
                    ),
                ),
            ),
        )

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.id).willReturn(shiftId)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(now.plusHours(1))
        given(shift.endAt).willReturn(now.plusHours(9))
        given(shift.member).willReturn(previousMember)
        given(previousMember.id).willReturn(10L)
        given(workplaceMemberRepository.findScopeById(targetMemberId)).willReturn(targetScope)
        given(targetScope.workplaceId).willReturn(workplaceId)
        given(targetScope.userId).willReturn(targetUserId)
        given(substituteRequestService.closeActiveRequestsByShiftChange(shiftId, now)).willReturn(
            listOf(ShiftChangeClosedRequest(1L, 10L, null)),
        )
        given(userRepository.findByIdForUpdate(targetUserId)).willReturn(Optional.of(targetUser))
        given(workplaceMemberRepository.findByIdForShare(targetMemberId)).willReturn(Optional.of(targetMember))
        given(targetMember.workplace).willReturn(workplace)
        given(targetMember.leftAt).willReturn(null)
        given(targetMember.user).willReturn(targetUser)
        given(targetMember.id).willReturn(targetMemberId)
        given(targetUser.id).willReturn(targetUserId)
        given(unavailableTime.startAt).willReturn(now.plusHours(2))
        given(unavailableTime.endAt).willReturn(now.plusHours(3))
        given(unavailableTimeRepository.findByUserId(targetUserId)).willReturn(listOf(unavailableTime))
        given(shiftRepository.existsOverlappingInSchedule(
            scheduleId, targetMemberId, now.plusHours(1), now.plusHours(9), shiftId,
        )).willReturn(false)
        given(shiftRepository.existsOverlappingOfficialShift(
            targetUserId, ScheduleStatus.PUBLISHED, now.plusHours(1), now.plusHours(9), shiftId,
        )).willReturn(false)

        val result = shiftService.reassignPublishedShift(
            workplaceId, scheduleId, shiftId, request, actorId,
        )

        assertThat(result.shift).isSameAs(shift)
        assertThat(result.closedSubstituteRequests).isEqualTo(1)
        verify(shift).changeMember(targetMember)
        val historyCaptor = argumentCaptor<ShiftAssignmentHistory>()
        verify(shiftAssignmentHistoryRepository).save(historyCaptor.capture())
        assertThat(historyCaptor.firstValue.shift).isSameAs(shift)
        assertThat(historyCaptor.firstValue.previousMember).isSameAs(previousMember)
        assertThat(historyCaptor.firstValue.newMember).isSameAs(targetMember)
        assertThat(historyCaptor.firstValue.reason).isEqualTo(ShiftAssignmentReason.MANAGER_REASSIGNED)
        assertThat(historyCaptor.firstValue.actorMember).isSameAs(actorMember)
        inOrder(
            scheduleRepository,
            shiftRepository,
            substituteRequestService,
            userRepository,
            workplaceMemberRepository,
        ) {
            verify(scheduleRepository).findByIdForUpdate(scheduleId)
            verify(shiftRepository).findByIdForUpdate(shiftId)
            verify(substituteRequestService).closeActiveRequestsByShiftChange(shiftId, now)
            verify(userRepository).findByIdForUpdate(targetUserId)
            verify(workplaceMemberRepository).findByIdForShare(targetMemberId)
        }
    }

    @Test
    @DisplayName("불가능 시간 충돌이 없으면 빈 확인 목록으로 공개 Shift 담당자를 변경한다")
    fun reassignPublishedShift_ReassignsWithEmptyAcknowledgementWhenNoConflict() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val targetMemberId = 20L
        val targetUserId = 30L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val startAt = now.plusHours(1)
        val endAt = now.plusHours(9)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()
        val previousMember = mock<WorkplaceMember>()
        val targetMember = mock<WorkplaceMember>()
        val targetUser = mock<User>()
        val targetScope = mock<WorkplaceMemberScope>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.id).willReturn(shiftId)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(startAt)
        given(shift.endAt).willReturn(endAt)
        given(shift.member).willReturn(previousMember)
        given(previousMember.id).willReturn(10L)
        given(workplaceMemberRepository.findScopeById(targetMemberId)).willReturn(targetScope)
        given(targetScope.workplaceId).willReturn(workplaceId)
        given(targetScope.userId).willReturn(targetUserId)
        given(substituteRequestService.closeActiveRequestsByShiftChange(shiftId, now)).willReturn(emptyList())
        given(userRepository.findByIdForUpdate(targetUserId)).willReturn(Optional.of(targetUser))
        given(workplaceMemberRepository.findByIdForShare(targetMemberId)).willReturn(Optional.of(targetMember))
        given(targetMember.workplace).willReturn(workplace)
        given(targetMember.leftAt).willReturn(null)
        given(targetMember.user).willReturn(targetUser)
        given(targetMember.id).willReturn(targetMemberId)
        given(targetUser.id).willReturn(targetUserId)
        given(shiftRepository.existsOverlappingInSchedule(
            scheduleId, targetMemberId, startAt, endAt, shiftId,
        )).willReturn(false)
        given(shiftRepository.existsOverlappingOfficialShift(
            targetUserId, ScheduleStatus.PUBLISHED, startAt, endAt, shiftId,
        )).willReturn(false)
        given(unavailableTimeRepository.findByUserId(targetUserId)).willReturn(emptyList())

        shiftService.reassignPublishedShift(
            workplaceId,
            scheduleId,
            shiftId,
            ShiftReassignRequest(targetMemberId, emptyList()),
            actorId,
        )

        verify(shift).changeMember(targetMember)
        verify(shiftAssignmentHistoryRepository).save(any())
    }

    @Test
    @DisplayName("동일한 담당자로 공개 Shift를 재배정할 수 없다")
    fun reassignPublishedShift_FailsWhenTargetIsCurrentMember() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val targetMemberId = 20L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()
        val currentMember = mock<WorkplaceMember>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(now.plusHours(1))
        given(shift.member).willReturn(currentMember)
        given(currentMember.id).willReturn(targetMemberId)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                workplaceId,
                scheduleId,
                shiftId,
                ShiftReassignRequest(targetMemberId, emptyList()),
                actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.SAME_SHIFT_MEMBER_REASSIGNMENT)
        verifyNoInteractions(substituteRequestService, userRepository, shiftAssignmentHistoryRepository)
        verify(shift, never()).changeMember(any())
    }

    @Test
    @DisplayName("대타 요청 종료에 실패하면 공개 Shift 담당자를 변경하거나 이력을 저장하지 않는다")
    fun reassignPublishedShift_DoesNotChangeMemberWhenClosingSubstituteRequestsFails() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val targetMemberId = 20L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()
        val previousMember = mock<WorkplaceMember>()
        val targetScope = mock<WorkplaceMemberScope>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.id).willReturn(shiftId)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(now.plusHours(1))
        given(shift.member).willReturn(previousMember)
        given(previousMember.id).willReturn(10L)
        given(workplaceMemberRepository.findScopeById(targetMemberId)).willReturn(targetScope)
        given(targetScope.workplaceId).willReturn(workplaceId)
        doThrow(IllegalStateException("V4 종료 실패"))
            .whenever(substituteRequestService)
            .closeActiveRequestsByShiftChange(shiftId, now)

        assertThatThrownBy {
            shiftService.reassignPublishedShift(
                workplaceId,
                scheduleId,
                shiftId,
                ShiftReassignRequest(targetMemberId, emptyList()),
                actorId,
            )
        }.isInstanceOf(IllegalStateException::class.java)

        verify(shift, never()).changeMember(any())
        verifyNoInteractions(shiftAssignmentHistoryRepository)
        verifyNoInteractions(userRepository)
    }

    @Test
    @DisplayName("다른 담당자의 확인 목록은 입력 오류로 거부하고 변경 작업을 시작하지 않는다")
    fun reassignPublishedShift_RejectsDifferentTargetMemberAcknowledgement() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val targetMemberId = 20L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()
        val previousMember = mock<WorkplaceMember>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.id).willReturn(shiftId)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(now.plusHours(1))
        given(shift.member).willReturn(previousMember)
        given(previousMember.id).willReturn(10L)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                workplaceId,
                scheduleId,
                shiftId,
                ShiftReassignRequest(
                    targetMemberId,
                    listOf(
                        AcknowledgedUnavailableConflict(
                            shiftId,
                            targetMemberId + 1,
                            listOf(UnavailableOverlap(now.plusHours(2), now.plusHours(3))),
                        ),
                    ),
                ),
                actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.INVALID_INPUT_VALUE)
        verify(shift, never()).changeMember(any())
        verifyNoInteractions(
            substituteRequestService,
            userRepository,
            shiftAssignmentHistoryRepository,
        )
    }

    @Test
    @DisplayName("확인 목록 중 하나라도 요청 대상과 다르면 입력 오류로 거부한다")
    fun reassignPublishedShift_RejectsAnyMismatchedAcknowledgement() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val targetMemberId = 20L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()
        val previousMember = mock<WorkplaceMember>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.id).willReturn(shiftId)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(now.plusHours(1))
        given(shift.member).willReturn(previousMember)
        given(previousMember.id).willReturn(10L)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                workplaceId,
                scheduleId,
                shiftId,
                ShiftReassignRequest(
                    targetMemberId,
                    listOf(
                        AcknowledgedUnavailableConflict(
                            shiftId,
                            targetMemberId,
                            listOf(UnavailableOverlap(now.plusHours(2), now.plusHours(3))),
                        ),
                        AcknowledgedUnavailableConflict(
                            shiftId + 1,
                            targetMemberId,
                            listOf(UnavailableOverlap(now.plusHours(2), now.plusHours(3))),
                        ),
                    ),
                ),
                actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.INVALID_INPUT_VALUE)
        verify(shift, never()).changeMember(any())
        verifyNoInteractions(
            substituteRequestService,
            userRepository,
            shiftAssignmentHistoryRepository,
        )
    }

    @ParameterizedTest
    @MethodSource("invalidAcknowledgedUnavailableOverlaps")
    @DisplayName("잘못된 확인 구간은 충돌 여부와 관계없이 입력 오류로 거부하고 변경 작업을 시작하지 않는다")
    fun reassignPublishedShift_RejectsInvalidAcknowledgedUnavailableOverlap(
        invalidOverlap: UnavailableOverlap,
    ) {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val targetMemberId = 20L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()
        val previousMember = mock<WorkplaceMember>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.id).willReturn(shiftId)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(now.plusHours(1))
        given(shift.member).willReturn(previousMember)
        given(previousMember.id).willReturn(10L)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                workplaceId,
                scheduleId,
                shiftId,
                ShiftReassignRequest(
                    targetMemberId,
                    listOf(
                        AcknowledgedUnavailableConflict(
                            shiftId,
                            targetMemberId,
                            listOf(invalidOverlap),
                        ),
                    ),
                ),
                actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.INVALID_INPUT_VALUE)
        verify(shift, never()).changeMember(any())
        verifyNoInteractions(
            substituteRequestService,
            userRepository,
            shiftAssignmentHistoryRepository,
        )
    }

    @Test
    @DisplayName("확인 구간이 현재 충돌 범위를 모두 포함하지 않으면 재확인을 요구한다")
    fun reassignPublishedShift_RequiresConfirmationForInsufficientAcknowledgement() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val targetMemberId = 20L
        val targetUserId = 30L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val startAt = now.plusHours(1)
        val endAt = now.plusHours(9)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()
        val previousMember = mock<WorkplaceMember>()
        val targetMember = mock<WorkplaceMember>()
        val targetUser = mock<User>()
        val targetScope = mock<WorkplaceMemberScope>()
        val unavailableTime = mock<com.merge.backend.domain.shift.entity.UnavailableTime>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.id).willReturn(shiftId)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(startAt)
        given(shift.endAt).willReturn(endAt)
        given(shift.member).willReturn(previousMember)
        given(previousMember.id).willReturn(10L)
        given(workplaceMemberRepository.findScopeById(targetMemberId)).willReturn(targetScope)
        given(targetScope.workplaceId).willReturn(workplaceId)
        given(targetScope.userId).willReturn(targetUserId)
        given(substituteRequestService.closeActiveRequestsByShiftChange(shiftId, now)).willReturn(emptyList())
        given(userRepository.findByIdForUpdate(targetUserId)).willReturn(Optional.of(targetUser))
        given(workplaceMemberRepository.findByIdForShare(targetMemberId)).willReturn(Optional.of(targetMember))
        given(targetMember.workplace).willReturn(workplace)
        given(targetMember.leftAt).willReturn(null)
        given(targetMember.user).willReturn(targetUser)
        given(targetMember.id).willReturn(targetMemberId)
        given(targetUser.id).willReturn(targetUserId)
        given(shiftRepository.existsOverlappingInSchedule(
            scheduleId, targetMemberId, startAt, endAt, shiftId,
        )).willReturn(false)
        given(shiftRepository.existsOverlappingOfficialShift(
            targetUserId, ScheduleStatus.PUBLISHED, startAt, endAt, shiftId,
        )).willReturn(false)
        given(unavailableTime.startAt).willReturn(now.plusHours(2))
        given(unavailableTime.endAt).willReturn(now.plusHours(3))
        given(unavailableTimeRepository.findByUserId(targetUserId)).willReturn(listOf(unavailableTime))

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                workplaceId,
                scheduleId,
                shiftId,
                ShiftReassignRequest(
                    targetMemberId,
                    listOf(
                        AcknowledgedUnavailableConflict(
                            shiftId,
                            targetMemberId,
                            listOf(UnavailableOverlap(now.plusHours(2), now.plusHours(2).plusMinutes(30))),
                        ),
                    ),
                ),
                actorId,
            )
        }

        assertThat(exception.errorCode)
            .isEqualTo(ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT_CONFIRMATION_REQUIRED)
        verify(shift, never()).changeMember(any())
        verifyNoInteractions(shiftAssignmentHistoryRepository)
    }

    @Test
    @DisplayName("다른 Shift의 확인 목록은 입력 오류로 거부하고 변경 작업을 시작하지 않는다")
    fun reassignPublishedShift_RejectsDifferentShiftAcknowledgement() {
        val workplaceId = 1L
        val scheduleId = 10L
        val shiftId = 100L
        val targetMemberId = 20L
        val actorId = 200L
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val shiftStartAt = now.plusHours(1)
        val workplace = mock<Workplace>()
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val actorMember = mock<WorkplaceMember>()
        val previousMember = mock<WorkplaceMember>()

        given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC))
        given(clock.zone).willReturn(ZoneOffset.UTC)
        given(workplaceMemberService.requireManager(actorId, workplaceId)).willReturn(actorMember)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shiftRepository.findByIdForUpdate(shiftId)).willReturn(Optional.of(shift))
        given(shift.id).willReturn(shiftId)
        given(shift.schedule).willReturn(schedule)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(shift.startAt).willReturn(shiftStartAt)
        given(shift.member).willReturn(previousMember)
        given(previousMember.id).willReturn(10L)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                workplaceId,
                scheduleId,
                shiftId,
                ShiftReassignRequest(
                    targetMemberId,
                    listOf(
                        AcknowledgedUnavailableConflict(
                            shiftId + 1,
                            targetMemberId,
                            listOf(UnavailableOverlap(shiftStartAt, now.plusHours(3))),
                        ),
                    ),
                ),
                actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.INVALID_INPUT_VALUE)
        verify(shift, never()).changeMember(any())
        verifyNoInteractions(
            substituteRequestService,
            userRepository,
            shiftAssignmentHistoryRepository,
        )
    }

    @Test
    fun createPublishedShift_SavesScheduledShiftWithoutAssignmentHistoryOrSubstituteClosure() {
        val fixture = publishedShiftCreateFixture()
        given(unavailableTimeRepository.findByUserId(fixture.targetUserId)).willReturn(emptyList())
        given(shiftRepository.save(any<Shift>())).willAnswer { it.getArgument<Shift>(0) }

        val result = shiftService.createPublishedShift(
            fixture.workplaceId,
            fixture.scheduleId,
            fixture.request(),
            fixture.actorId,
        )

        assertThat(result.status).isEqualTo(ShiftStatus.SCHEDULED)
        assertThat(result.schedule).isSameAs(fixture.schedule)
        assertThat(result.member).isSameAs(fixture.targetMember)
        verify(shiftRepository).save(any<Shift>())
        verifyNoInteractions(substituteRequestService, shiftAssignmentHistoryRepository)
    }

    @Test
    fun createPublishedShift_RejectsLocalScheduleOverlapWithSft009() {
        val fixture = publishedShiftCreateFixture()
        given(shiftRepository.existsOverlappingInSchedule(
            fixture.scheduleId, fixture.memberId, fixture.startAt, fixture.endAt, null,
        )).willReturn(true)

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.DUPLICATE_LOCAL_SCHEDULE_TIME)
        verify(shiftRepository, never()).save(any())
        verifyNoInteractions(substituteRequestService, shiftAssignmentHistoryRepository)
    }

    @Test
    fun createPublishedShift_ReturnsSft021WithNewShiftKeyWhenUnavailableConflictIsUnacknowledged() {
        val fixture = publishedShiftCreateFixture()
        val unavailableTime = mock<com.merge.backend.domain.shift.entity.UnavailableTime>()
        given(fixture.targetUser.name).willReturn("직원")
        given(unavailableTime.startAt).willReturn(fixture.startAt.plusHours(1))
        given(unavailableTime.endAt).willReturn(fixture.startAt.plusHours(2))
        given(unavailableTimeRepository.findByUserId(fixture.targetUserId)).willReturn(listOf(unavailableTime))

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode)
            .isEqualTo(ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT_CONFIRMATION_REQUIRED)
        val response = exception.data as PublishedShiftConfirmationRequiredResponse
        assertThat(response.requiresConfirmation).isTrue()
        assertThat(response.unavailableConflicts.single().newShiftKey)
            .isEqualTo(NewShiftKey(fixture.scheduleId, fixture.memberId, fixture.startAt, fixture.endAt))
        verify(shiftRepository, never()).save(any())
    }

    @Test
    fun createPublishedShift_RejectsInvalidAcknowledgementBeforeTargetLockOrWrite() {
        val fixture = publishedShiftCreateFixture(
            withTargetLocks = false,
            withTimeValidationStubs = false,
        )
        val invalidRequest = fixture.request(
            listOf(
                AcknowledgedNewShiftUnavailableConflict(
                    NewShiftKey(fixture.scheduleId, fixture.memberId, fixture.startAt, fixture.endAt),
                    listOf(UnavailableOverlap(fixture.startAt.plusHours(2), fixture.startAt.plusHours(2))),
                ),
            ),
        )

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, invalidRequest, fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.INVALID_INPUT_VALUE)
        verifyNoInteractions(userRepository, substituteRequestService, shiftAssignmentHistoryRepository)
        verify(shiftRepository, never()).save(any())
    }

    @Test
    fun createPublishedShift_RejectsCurrentStartTimeWithSft020() {
        val fixture = publishedShiftCreateFixture()
        val request = PublishedShiftCreateRequest(
            fixture.memberId,
            fixture.now,
            fixture.now.plusHours(1),
            emptyList(),
        )

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, request, fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        verify(shiftRepository, never()).save(any())
        verifyNoInteractions(userRepository, substituteRequestService, shiftAssignmentHistoryRepository)
    }

    @Test
    fun createPublishedShift_RechecksStartTimeAfterTargetLocksBeforeSaving() {
        val fixture = publishedShiftCreateFixture()
        given(clock.instant()).willReturn(
            fixture.now.toInstant(ZoneOffset.UTC),
            fixture.startAt.toInstant(ZoneOffset.UTC),
        )
        given(unavailableTimeRepository.findByUserId(fixture.targetUserId)).willReturn(emptyList())

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        verify(userRepository).findByIdForUpdate(fixture.targetUserId)
        verify(workplaceMemberRepository).findByIdForShare(fixture.memberId)
        verify(shiftRepository, never()).save(any())
    }

    @Test
    fun createPublishedShift_RejectsStartedShiftBeforeUnavailableConflictCheck() {
        val fixture = publishedShiftCreateFixture()
        val unavailableTime = mock<com.merge.backend.domain.shift.entity.UnavailableTime>()
        Mockito.lenient().`when`(unavailableTime.startAt).thenReturn(fixture.now.minusHours(1))
        Mockito.lenient().`when`(unavailableTime.endAt).thenReturn(fixture.now.plusHours(1))
        Mockito.lenient().`when`(unavailableTimeRepository.findByUserId(fixture.targetUserId))
            .thenReturn(listOf(unavailableTime))
        val request = PublishedShiftCreateRequest(
            fixture.memberId,
            fixture.now,
            fixture.now.plusHours(1),
            emptyList(),
        )

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, request, fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        verifyNoInteractions(unavailableTimeRepository, userRepository, substituteRequestService)
        verify(shiftRepository, never()).save(any())
    }

    @Test
    fun createPublishedShift_SavesShiftWhenAllUnavailableConflictsAreAcknowledged() {
        val fixture = publishedShiftCreateFixture()
        val unavailableTime = mock<com.merge.backend.domain.shift.entity.UnavailableTime>()
        val overlapStartAt = fixture.startAt.plusHours(1)
        val overlapEndAt = fixture.startAt.plusHours(2)
        given(unavailableTime.startAt).willReturn(overlapStartAt)
        given(unavailableTime.endAt).willReturn(overlapEndAt)
        given(unavailableTimeRepository.findByUserId(fixture.targetUserId)).willReturn(listOf(unavailableTime))
        given(shiftRepository.save(any<Shift>())).willAnswer { it.getArgument<Shift>(0) }

        val result = shiftService.createPublishedShift(
            fixture.workplaceId,
            fixture.scheduleId,
            fixture.request(listOf(newShiftAcknowledgement(fixture, overlapStartAt, overlapEndAt))),
            fixture.actorId,
        )

        assertThat(result.status).isEqualTo(ShiftStatus.SCHEDULED)
        verify(shiftRepository).save(any<Shift>())
    }

    @Test
    fun createPublishedShift_ReturnsSft021WhenAcknowledgementOnlyPartiallyCoversCurrentConflict() {
        val fixture = publishedShiftCreateFixture()
        val unavailableTime = mock<com.merge.backend.domain.shift.entity.UnavailableTime>()
        given(unavailableTime.startAt).willReturn(fixture.startAt.plusHours(1))
        given(unavailableTime.endAt).willReturn(fixture.startAt.plusHours(3))
        given(unavailableTimeRepository.findByUserId(fixture.targetUserId)).willReturn(listOf(unavailableTime))

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId,
                fixture.scheduleId,
                fixture.request(listOf(newShiftAcknowledgement(
                    fixture,
                    fixture.startAt.plusHours(1),
                    fixture.startAt.plusHours(2),
                ))),
                fixture.actorId,
            )
        }

        assertThat(exception.errorCode)
            .isEqualTo(ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT_CONFIRMATION_REQUIRED)
        verify(shiftRepository, never()).save(any())
    }

    @Test
    fun createPublishedShift_RejectsAcknowledgementForDifferentNewShiftKey() {
        val fixture = publishedShiftCreateFixture(
            withTargetLocks = false,
            withTimeValidationStubs = false,
        )
        val acknowledgement = AcknowledgedNewShiftUnavailableConflict(
            NewShiftKey(fixture.scheduleId + 1, fixture.memberId, fixture.startAt, fixture.endAt),
            listOf(UnavailableOverlap(fixture.startAt.plusHours(1), fixture.startAt.plusHours(2))),
        )

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId,
                fixture.scheduleId,
                fixture.request(listOf(acknowledgement)),
                fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.INVALID_INPUT_VALUE)
        verifyNoInteractions(userRepository, substituteRequestService, shiftAssignmentHistoryRepository)
    }

    @Test
    fun createPublishedShift_RequiresManagerPermission() {
        val fixture = publishedShiftCreateFixture(
            withTargetLocks = false,
            withTimeValidationStubs = false,
            withScheduleStubs = false,
        )
        doThrow(BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED))
            .whenever(workplaceMemberService)
            .requireManager(fixture.actorId, fixture.workplaceId)

        assertThatThrownBy {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.request(), fixture.actorId,
            )
        }.isInstanceOf(BusinessException::class.java)
        verifyNoInteractions(scheduleRepository, userRepository)
    }

    @Test
    fun createPublishedShift_RejectsNonPublishedSchedule() {
        val fixture = publishedShiftCreateFixture(
            withTargetLocks = false,
            withTimeValidationStubs = false,
        )
        given(fixture.schedule.status).willReturn(ScheduleStatus.DRAFT)

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun createPublishedShift_RejectsMemberOutsideWorkplace() {
        val fixture = publishedShiftCreateFixture()
        given(fixture.targetScope.workplaceId).willReturn(fixture.workplaceId + 1)

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.NOT_FOUND_ERROR)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun createPublishedShift_RejectsEndedMember() {
        val fixture = publishedShiftCreateFixture()
        given(fixture.targetMember.leftAt).willReturn(fixture.now)

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.NOT_FOUND_ERROR)
        verify(shiftRepository, never()).save(any())
    }

    @Test
    fun createPublishedShift_RejectsTimeOutsideScheduleWeek() {
        val fixture = publishedShiftCreateFixture(withTargetLocks = false)
        val request = PublishedShiftCreateRequest(
            fixture.memberId,
            fixture.startAt.plusWeeks(1),
            fixture.endAt.plusWeeks(1),
            emptyList(),
        )

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, request, fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.INVALID_SHIFT_INSCHEDULE)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun createPublishedShift_RejectsGlobalOfficialShiftOverlapWithSft010() {
        val fixture = publishedShiftCreateFixture()
        given(shiftRepository.existsOverlappingOfficialShift(
            fixture.targetUserId, ScheduleStatus.PUBLISHED, fixture.startAt, fixture.endAt, null,
        )).willReturn(true)

        val exception = assertThrows<BusinessException> {
            shiftService.createPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.DUPLICATE_GLOBAL_SCHEDULE_TIME)
        verify(shiftRepository, never()).save(any())
    }

    @Test
    fun reassignPublishedShift_RejectsNonManagerBeforeAcquiringPublishedShiftLocks() {
        val fixture = publishedShiftReassignmentFixture(withAuthorization = false)
        val exception = BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED)
        given(workplaceMemberService.requireManager(fixture.actorId, fixture.workplaceId)).willThrow(exception)

        assertThatThrownBy {
            shiftService.reassignPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.shiftId, fixture.request(), fixture.actorId,
            )
        }.isSameAs(exception)

        verifyNoInteractions(scheduleRepository, shiftRepository, substituteRequestService, shiftAssignmentHistoryRepository)
    }

    @Test
    fun reassignPublishedShift_RejectsTargetMemberOutsideWorkplace() {
        val fixture = publishedShiftReassignmentFixture()
        given(fixture.targetScope.workplaceId).willReturn(fixture.workplaceId + 1)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.shiftId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.NOT_FOUND_ERROR)
        verifyNoInteractions(substituteRequestService, userRepository, shiftAssignmentHistoryRepository)
        verify(fixture.shift, never()).changeMember(any())
    }

    @Test
    fun reassignPublishedShift_RejectsEndedTargetMember() {
        val fixture = publishedShiftReassignmentFixture()
        given(fixture.targetMember.leftAt).willReturn(fixture.now)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.shiftId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.NOT_FOUND_ERROR)
        verify(fixture.shift, never()).changeMember(any())
        verifyNoInteractions(shiftAssignmentHistoryRepository)
    }

    @Test
    fun reassignPublishedShift_RejectsStartedShiftWithSft020() {
        val fixture = publishedShiftReassignmentFixture()
        given(fixture.shift.startAt).willReturn(fixture.now)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.shiftId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        verifyNoInteractions(substituteRequestService, userRepository, shiftAssignmentHistoryRepository)
    }

    @Test
    fun reassignPublishedShift_RejectsDraftScheduleWithSft020() {
        val fixture = publishedShiftReassignmentFixture()
        given(fixture.schedule.status).willReturn(ScheduleStatus.DRAFT)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.shiftId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)
        verifyNoInteractions(shiftRepository, substituteRequestService, userRepository, shiftAssignmentHistoryRepository)
    }

    @Test
    fun reassignPublishedShift_RejectsLocalScheduleOverlapWithSft009() {
        val fixture = publishedShiftReassignmentFixture()
        given(shiftRepository.existsOverlappingInSchedule(
            fixture.scheduleId, fixture.targetMemberId, fixture.startAt, fixture.endAt, fixture.shiftId,
        )).willReturn(true)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.shiftId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.DUPLICATE_LOCAL_SCHEDULE_TIME)
        verify(fixture.shift, never()).changeMember(any())
        verifyNoInteractions(shiftAssignmentHistoryRepository)
    }

    @Test
    fun reassignPublishedShift_RejectsGlobalOfficialShiftOverlapWithSft010() {
        val fixture = publishedShiftReassignmentFixture()
        given(shiftRepository.existsOverlappingOfficialShift(
            fixture.targetUserId, ScheduleStatus.PUBLISHED, fixture.startAt, fixture.endAt, fixture.shiftId,
        )).willReturn(true)

        val exception = assertThrows<BusinessException> {
            shiftService.reassignPublishedShift(
                fixture.workplaceId, fixture.scheduleId, fixture.shiftId, fixture.request(), fixture.actorId,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.DUPLICATE_GLOBAL_SCHEDULE_TIME)
        verify(fixture.shift, never()).changeMember(any())
        verifyNoInteractions(shiftAssignmentHistoryRepository)
    }

    private fun newShiftAcknowledgement(
        fixture: PublishedShiftCreateFixture,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
    ) = AcknowledgedNewShiftUnavailableConflict(
        NewShiftKey(fixture.scheduleId, fixture.memberId, fixture.startAt, fixture.endAt),
        listOf(UnavailableOverlap(startAt, endAt)),
    )

    private fun publishedShiftReassignmentFixture(
        withAuthorization: Boolean = true,
    ): PublishedShiftReassignmentFixture {
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val fixture = PublishedShiftReassignmentFixture(
            workplaceId = 1L,
            scheduleId = 10L,
            shiftId = 100L,
            targetMemberId = 20L,
            targetUserId = 30L,
            actorId = 200L,
            now = now,
            startAt = now.plusHours(1),
            endAt = now.plusHours(5),
            workplace = mock(),
            schedule = mock(),
            shift = mock(),
            actorMember = mock(),
            previousMember = mock(),
            targetMember = mock(),
            targetUser = mock(),
            targetScope = mock(),
        )
        Mockito.lenient().`when`(clock.instant()).thenReturn(now.toInstant(ZoneOffset.UTC))
        Mockito.lenient().`when`(clock.zone).thenReturn(ZoneOffset.UTC)
        if (withAuthorization) {
            Mockito.lenient().`when`(workplaceMemberService.requireManager(fixture.actorId, fixture.workplaceId))
                .thenReturn(fixture.actorMember)
        }
        Mockito.lenient().`when`(scheduleRepository.findByIdForUpdate(fixture.scheduleId))
            .thenReturn(Optional.of(fixture.schedule))
        Mockito.lenient().`when`(fixture.schedule.workplace).thenReturn(fixture.workplace)
        Mockito.lenient().`when`(fixture.schedule.id).thenReturn(fixture.scheduleId)
        Mockito.lenient().`when`(fixture.workplace.id).thenReturn(fixture.workplaceId)
        Mockito.lenient().`when`(fixture.schedule.status).thenReturn(ScheduleStatus.PUBLISHED)
        Mockito.lenient().`when`(shiftRepository.findByIdForUpdate(fixture.shiftId)).thenReturn(Optional.of(fixture.shift))
        Mockito.lenient().`when`(fixture.shift.id).thenReturn(fixture.shiftId)
        Mockito.lenient().`when`(fixture.shift.schedule).thenReturn(fixture.schedule)
        Mockito.lenient().`when`(fixture.shift.status).thenReturn(ShiftStatus.SCHEDULED)
        Mockito.lenient().`when`(fixture.shift.startAt).thenReturn(fixture.startAt)
        Mockito.lenient().`when`(fixture.shift.endAt).thenReturn(fixture.endAt)
        Mockito.lenient().`when`(fixture.shift.member).thenReturn(fixture.previousMember)
        Mockito.lenient().`when`(fixture.previousMember.id).thenReturn(10L)
        Mockito.lenient().`when`(workplaceMemberRepository.findScopeById(fixture.targetMemberId))
            .thenReturn(fixture.targetScope)
        Mockito.lenient().`when`(fixture.targetScope.workplaceId).thenReturn(fixture.workplaceId)
        Mockito.lenient().`when`(fixture.targetScope.userId).thenReturn(fixture.targetUserId)
        Mockito.lenient().`when`(substituteRequestService.closeActiveRequestsByShiftChange(fixture.shiftId, fixture.now))
            .thenReturn(emptyList())
        Mockito.lenient().`when`(userRepository.findByIdForUpdate(fixture.targetUserId)).thenReturn(Optional.of(fixture.targetUser))
        Mockito.lenient().`when`(workplaceMemberRepository.findByIdForShare(fixture.targetMemberId))
            .thenReturn(Optional.of(fixture.targetMember))
        Mockito.lenient().`when`(fixture.targetMember.workplace).thenReturn(fixture.workplace)
        Mockito.lenient().`when`(fixture.targetMember.leftAt).thenReturn(null)
        Mockito.lenient().`when`(fixture.targetMember.user).thenReturn(fixture.targetUser)
        Mockito.lenient().`when`(fixture.targetMember.id).thenReturn(fixture.targetMemberId)
        Mockito.lenient().`when`(fixture.targetUser.id).thenReturn(fixture.targetUserId)
        Mockito.lenient().`when`(shiftRepository.existsOverlappingInSchedule(
            fixture.scheduleId, fixture.targetMemberId, fixture.startAt, fixture.endAt, fixture.shiftId,
        )).thenReturn(false)
        Mockito.lenient().`when`(shiftRepository.existsOverlappingOfficialShift(
            fixture.targetUserId, ScheduleStatus.PUBLISHED, fixture.startAt, fixture.endAt, fixture.shiftId,
        )).thenReturn(false)
        Mockito.lenient().`when`(unavailableTimeRepository.findByUserId(fixture.targetUserId)).thenReturn(emptyList())
        return fixture
    }

    private data class PublishedShiftReassignmentFixture(
        val workplaceId: Long,
        val scheduleId: Long,
        val shiftId: Long,
        val targetMemberId: Long,
        val targetUserId: Long,
        val actorId: Long,
        val now: LocalDateTime,
        val startAt: LocalDateTime,
        val endAt: LocalDateTime,
        val workplace: Workplace,
        val schedule: Schedule,
        val shift: Shift,
        val actorMember: WorkplaceMember,
        val previousMember: WorkplaceMember,
        val targetMember: WorkplaceMember,
        val targetUser: User,
        val targetScope: WorkplaceMemberScope,
    ) {
        fun request() = ShiftReassignRequest(targetMemberId, emptyList())
    }

    private fun publishedShiftCreateFixture(
        withTargetLocks: Boolean = true,
        withTimeValidationStubs: Boolean = true,
        withScheduleStubs: Boolean = true,
    ): PublishedShiftCreateFixture {
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val fixture = PublishedShiftCreateFixture(
            workplaceId = 1L,
            scheduleId = 10L,
            memberId = 20L,
            targetUserId = 30L,
            actorId = 200L,
            now = now,
            startAt = now.plusHours(1),
            endAt = now.plusHours(5),
            workplace = mock(),
            schedule = mock(),
            actorMember = mock(),
            targetMember = mock(),
            targetUser = mock(),
            targetScope = mock(),
        )
        if (withTimeValidationStubs) {
            Mockito.lenient().`when`(clock.instant()).thenReturn(now.toInstant(ZoneOffset.UTC))
            Mockito.lenient().`when`(clock.zone).thenReturn(ZoneOffset.UTC)
        }
        if (withScheduleStubs) {
            Mockito.lenient().`when`(workplaceMemberService.requireManager(fixture.actorId, fixture.workplaceId))
                .thenReturn(fixture.actorMember)
            Mockito.lenient().`when`(scheduleRepository.findByIdForUpdate(fixture.scheduleId))
                .thenReturn(Optional.of(fixture.schedule))
            Mockito.lenient().`when`(fixture.schedule.workplace).thenReturn(fixture.workplace)
            Mockito.lenient().`when`(fixture.schedule.id).thenReturn(fixture.scheduleId)
            Mockito.lenient().`when`(fixture.workplace.id).thenReturn(fixture.workplaceId)
            Mockito.lenient().`when`(fixture.schedule.status).thenReturn(ScheduleStatus.PUBLISHED)
            if (withTimeValidationStubs) {
                Mockito.lenient().`when`(fixture.schedule.weekStartDate).thenReturn(now.toLocalDate())
            }
        }
        if (withTargetLocks) {
            Mockito.lenient().`when`(workplaceMemberRepository.findScopeById(fixture.memberId)).thenReturn(fixture.targetScope)
            Mockito.lenient().`when`(fixture.targetScope.workplaceId).thenReturn(fixture.workplaceId)
            Mockito.lenient().`when`(fixture.targetScope.userId).thenReturn(fixture.targetUserId)
            Mockito.lenient().`when`(userRepository.findByIdForUpdate(fixture.targetUserId)).thenReturn(Optional.of(fixture.targetUser))
            Mockito.lenient().`when`(workplaceMemberRepository.findByIdForShare(fixture.memberId)).thenReturn(Optional.of(fixture.targetMember))
            Mockito.lenient().`when`(fixture.targetMember.workplace).thenReturn(fixture.workplace)
            Mockito.lenient().`when`(fixture.targetMember.leftAt).thenReturn(null)
            Mockito.lenient().`when`(fixture.targetMember.user).thenReturn(fixture.targetUser)
            Mockito.lenient().`when`(fixture.targetMember.id).thenReturn(fixture.memberId)
            Mockito.lenient().`when`(fixture.targetUser.id).thenReturn(fixture.targetUserId)
        }
        return fixture
    }

    private data class PublishedShiftCreateFixture(
        val workplaceId: Long,
        val scheduleId: Long,
        val memberId: Long,
        val targetUserId: Long,
        val actorId: Long,
        val now: LocalDateTime,
        val startAt: LocalDateTime,
        val endAt: LocalDateTime,
        val workplace: Workplace,
        val schedule: Schedule,
        val actorMember: WorkplaceMember,
        val targetMember: WorkplaceMember,
        val targetUser: User,
        val targetScope: WorkplaceMemberScope,
    ) {
        fun request(
            acknowledgedUnavailableConflicts: List<AcknowledgedNewShiftUnavailableConflict> = emptyList(),
        ) = PublishedShiftCreateRequest(memberId, startAt, endAt, acknowledgedUnavailableConflicts)
    }

    companion object {
        @JvmStatic
        fun invalidAcknowledgedUnavailableOverlaps(): Stream<UnavailableOverlap> = Stream.of(
            UnavailableOverlap(null, LocalDateTime.of(2026, 10, 28, 17, 0)),
            UnavailableOverlap(LocalDateTime.of(2026, 10, 28, 16, 0), null),
            UnavailableOverlap(
                LocalDateTime.of(2026, 10, 28, 17, 0),
                LocalDateTime.of(2026, 10, 28, 16, 0),
            ),
            UnavailableOverlap(
                LocalDateTime.of(2026, 10, 28, 16, 0),
                LocalDateTime.of(2026, 10, 28, 16, 0),
            ),
        )
    }

}
