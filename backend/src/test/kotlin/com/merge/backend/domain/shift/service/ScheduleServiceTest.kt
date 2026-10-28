package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.entity.RegularShiftPattern
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.event.SchedulePublishedEvent
import com.merge.backend.domain.shift.exception.ScheduleErrorCode
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.RegularShiftPatternRepository
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.domain.workplace.service.WorkplaceMemberService
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.given
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.context.ApplicationEventPublisher
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import org.mockito.Mockito
import org.junit.jupiter.api.BeforeEach

@ExtendWith(MockitoExtension::class)
class ScheduleServiceTest {

    @Mock
    private lateinit var scheduleRepository: ScheduleRepository

    @Mock
    private lateinit var regularShiftPatternRepository: RegularShiftPatternRepository

    @Mock
    private lateinit var shiftRepository: ShiftRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var shiftService: ShiftService

    @Mock
    private lateinit var workplaceMemberService: WorkplaceMemberService

    @Mock
    private lateinit var workplaceRepository: WorkplaceRepository

    @Mock
    private lateinit var unavailableTimeRepository: UnavailableTimeRepository

    @Mock
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    // Pattern 복사의 소속 Guard(FOR SHARE 재확인)용. Mock에서는 기본으로 "현재 소속" (실제 Lock 동작은 DraftMembershipGuardConcurrencyTest)
    @Mock
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @BeforeEach
    fun allowCurrentMembership() {
        Mockito.lenient().`when`(workplaceMemberRepository.existsCurrentMember(any(), any())).thenReturn(true)
    }

    @Mock
    private lateinit var clock: Clock

    @InjectMocks
    private lateinit var scheduleService: ScheduleService

    @Test
    fun 주_시작일이_월요일이_아니면_예외가_발생한다() {
        val actorUserId = 1L
        val workplaceId = 1L
        val weekStartDate = LocalDate.of(2026, 9, 22)

        val exception = assertThrows<BusinessException> {
            scheduleService.createDraftSchedule(actorUserId, workplaceId, weekStartDate)
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.INVALID_WEEK_START_DATE)
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        verifyNoInteractions(scheduleRepository)
    }

    @Test
    fun 같은_주차의_스케줄이_이미_존재하면_예외가_발생한다() {
        val actorUserId = 1L
        val workplaceId = 1L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val workplace = mock<Workplace>()
        val existingSchedule = mock<Schedule>()

        given(workplaceRepository.findByIdForUpdate(workplaceId)).willReturn(Optional.of(workplace))
        given(scheduleRepository.findByWorkplaceAndWeekStartDateForUpdate(workplaceId, weekStartDate))
            .willReturn(Optional.of(existingSchedule))

        val exception = assertThrows<BusinessException> {
            scheduleService.createDraftSchedule(actorUserId, workplaceId, weekStartDate)
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.SCHEDULE_ALREADY_EXISTS)
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        val lockOrder = inOrder(workplaceRepository, scheduleRepository)
        lockOrder.verify(workplaceRepository).findByIdForUpdate(workplaceId)
        lockOrder.verify(scheduleRepository).findByWorkplaceAndWeekStartDateForUpdate(workplaceId, weekStartDate)
    }

    @Test
    @DisplayName("SCH-01 - 유효한 요청이면 빈 DRAFT Schedule 생성 결과를 반환한다")
    fun createDraftScheduleCreatesAndReturnsEmptyDraftSchedule() {
        val actorUserId = 1L
        val workplaceId = 10L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val workplace = mock<Workplace>()
        val savedSchedule = Schedule(workplace, weekStartDate)

        given(workplaceRepository.findByIdForUpdate(workplaceId)).willReturn(Optional.of(workplace))
        given(workplace.id).willReturn(workplaceId)
        given(scheduleRepository.findByWorkplaceAndWeekStartDateForUpdate(workplaceId, weekStartDate))
            .willReturn(Optional.empty())
        given(scheduleRepository.save(any<Schedule>())).willReturn(savedSchedule)
        given(regularShiftPatternRepository.findByMemberWorkplaceIdAndMemberLeftAtIsNull(workplaceId))
            .willReturn(emptyList())

        val result = scheduleService.createDraftSchedule(actorUserId, workplaceId, weekStartDate)

        assertThat(result).isNotNull()
        assertThat(result.workplaceId).isEqualTo(workplaceId)
        assertThat(result.weekStartDate).isEqualTo(weekStartDate)
        assertThat(result.status).isEqualTo(ScheduleStatus.DRAFT)
        assertThat(result.shiftCount).isZero()
        verifyNoInteractions(shiftService)
    }

    @Test
    @DisplayName("SCH-01 - Pattern을 해당 주차의 실제 Shift 시간으로 변환한다")
    fun createDraftScheduleConvertsPatternToShiftDateTime() {
        val actorUserId = 1L
        val workplaceId = 10L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val workplace = mock<Workplace>()
        val patternMember = mock<WorkplaceMember>()
        val pattern = mock<RegularShiftPattern>()
        val savedSchedule = Schedule(workplace, weekStartDate)

        given(workplaceRepository.findByIdForUpdate(workplaceId)).willReturn(Optional.of(workplace))
        given(workplace.id).willReturn(workplaceId)
        given(scheduleRepository.findByWorkplaceAndWeekStartDateForUpdate(workplaceId, weekStartDate))
            .willReturn(Optional.empty())
        given(scheduleRepository.save(any<Schedule>())).willReturn(savedSchedule)
        given(regularShiftPatternRepository.findByMemberWorkplaceIdAndMemberLeftAtIsNull(workplaceId))
            .willReturn(listOf(pattern))
        given(pattern.dayOfWeek).willReturn(DayOfWeek.WEDNESDAY)
        given(pattern.startTime).willReturn(LocalTime.of(9, 0))
        given(pattern.endTime).willReturn(LocalTime.of(14, 0))
        given(pattern.member).willReturn(patternMember)

        val result = scheduleService.createDraftSchedule(actorUserId, workplaceId, weekStartDate)

        assertThat(result).isNotNull()
        assertThat(result.workplaceId).isEqualTo(workplaceId)
        assertThat(result.weekStartDate).isEqualTo(weekStartDate)
        assertThat(result.status).isEqualTo(ScheduleStatus.DRAFT)
        assertThat(result.shiftCount).isEqualTo(1)
        verify(shiftService).createFromValidatedPattern(
            savedSchedule,
            patternMember,
            LocalDateTime.of(2026, 9, 23, 9, 0),
            LocalDateTime.of(2026, 9, 23, 14, 0),
        )
    }

    @Test
    @DisplayName("SCH-05 - Schedule이 없으면 null을 반환한다")
    fun getWeeklyScheduleReturnsNullWhenScheduleDoesNotExist() {
        val actorUserId = 1L
        val workplaceId = 10L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        given(scheduleRepository.findByWorkplace_IdAndWeekStartDate(workplaceId, weekStartDate))
            .willReturn(Optional.empty())

        val result = scheduleService.getWeeklySchedule(actorUserId, workplaceId, weekStartDate)

        assertThat(result).isNull()
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        verify(scheduleRepository).findByWorkplace_IdAndWeekStartDate(workplaceId, weekStartDate)
        verifyNoInteractions(shiftRepository)
    }

    @Test
    @DisplayName("SCH-05 - Schedule은 존재하지만 Shift가 없으면 빈 shifts를 반환한다")
    fun getWeeklyScheduleReturnsEmptyShiftsWhenScheduleExistsWithoutShift() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val schedule = mock<Schedule>()
        val workplace = mock<Workplace>()
        given(schedule.id).willReturn(scheduleId)
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.weekStartDate).willReturn(weekStartDate)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(scheduleRepository.findByWorkplace_IdAndWeekStartDate(workplaceId, weekStartDate))
            .willReturn(Optional.of(schedule))
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(emptyList())

        val result = requireNotNull(scheduleService.getWeeklySchedule(actorUserId, workplaceId, weekStartDate))

        assertThat(result.scheduleId).isEqualTo(scheduleId)
        assertThat(result.workplaceId).isEqualTo(workplaceId)
        assertThat(result.weekStartDate).isEqualTo(weekStartDate)
        assertThat(result.status).isEqualTo(ScheduleStatus.DRAFT)
        assertThat(result.publishedAt).isNull()
        assertThat(result.shifts).isEmpty()
        verify(scheduleRepository).findByWorkplace_IdAndWeekStartDate(workplaceId, weekStartDate)
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)
    }

    @Test
    @DisplayName("SCH-05 - Schedule에 Shift가 있으면 Shift 정보를 DTO로 반환한다")
    fun getWeeklyScheduleReturnsShiftResponsesWhenShiftsExist() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val shiftId = 101L
        val memberId = 30L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val startAt = LocalDateTime.of(2026, 9, 21, 9, 0)
        val endAt = LocalDateTime.of(2026, 9, 21, 14, 0)
        val schedule = mock<Schedule>()
        val workplace = mock<Workplace>()
        val shift = mock<Shift>()
        val member = mock<WorkplaceMember>()
        val user = mock<User>()
        given(schedule.id).willReturn(scheduleId)
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.weekStartDate).willReturn(weekStartDate)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(shift.id).willReturn(shiftId)
        given(shift.member).willReturn(member)
        given(member.id).willReturn(memberId)
        given(member.user).willReturn(user)
        given(user.name).willReturn("김민수")
        given(member.role).willReturn(WorkplaceRole.EMPLOYEE)
        given(shift.startAt).willReturn(startAt)
        given(shift.endAt).willReturn(endAt)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(scheduleRepository.findByWorkplace_IdAndWeekStartDate(workplaceId, weekStartDate))
            .willReturn(Optional.of(schedule))
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(listOf(shift))

        val result = requireNotNull(scheduleService.getWeeklySchedule(actorUserId, workplaceId, weekStartDate))

        assertThat(result.shifts).hasSize(1)
        val shiftResponse = requireNotNull(result.shifts)[0]
        assertThat(shiftResponse.shiftId).isEqualTo(shiftId)
        assertThat(shiftResponse.memberId).isEqualTo(memberId)
        assertThat(shiftResponse.memberName).isEqualTo("김민수")
        assertThat(shiftResponse.role).isEqualTo(WorkplaceRole.EMPLOYEE)
        assertThat(shiftResponse.startAt).isEqualTo(startAt)
        assertThat(shiftResponse.endAt).isEqualTo(endAt)
        assertThat(shiftResponse.status).isEqualTo(ShiftStatus.SCHEDULED)
        verify(scheduleRepository).findByWorkplace_IdAndWeekStartDate(workplaceId, weekStartDate)
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)
    }

    @Test
    @DisplayName("SCH-05 - weekStartDate가 월요일이 아니면 예외가 발생한다")
    fun getWeeklyScheduleThrowsExceptionWhenWeekStartDateIsNotMonday() {
        val actorUserId = 1L
        val workplaceId = 10L
        val weekStartDate = LocalDate.of(2026, 9, 22)

        val exception = assertThrows<BusinessException> {
            scheduleService.getWeeklySchedule(actorUserId, workplaceId, weekStartDate)
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.INVALID_WEEK_START_DATE)
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        verifyNoInteractions(scheduleRepository, shiftRepository)
    }

    @Test
    @DisplayName("SCH-05 - MANAGER가 아니면 Schedule을 조회하지 않고 예외가 발생한다")
    fun getWeeklyScheduleThrowsExceptionWhenActorIsNotManager() {
        val actorUserId = 1L
        val workplaceId = 10L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        given(workplaceMemberService.requireManager(actorUserId, workplaceId))
            .willThrow(BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED))

        val exception = assertThrows<BusinessException> {
            scheduleService.getWeeklySchedule(actorUserId, workplaceId, weekStartDate)
        }

        assertThat(exception.errorCode).isEqualTo(WorkplaceErrorCode.MANAGER_REQUIRED)
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        verifyNoInteractions(scheduleRepository, shiftRepository)
    }

    @Test
    @DisplayName("SCH-05 - PUBLISHED Schedule도 publishedAt과 함께 조회한다")
    fun getWeeklyScheduleReturnsPublishedSchedule() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val publishedAt = LocalDateTime.of(2026, 9, 19, 18, 30)
        val schedule = mock<Schedule>()
        val workplace = mock<Workplace>()
        given(schedule.id).willReturn(scheduleId)
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.weekStartDate).willReturn(weekStartDate)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(schedule.publishedAt).willReturn(publishedAt)
        given(scheduleRepository.findByWorkplace_IdAndWeekStartDate(workplaceId, weekStartDate))
            .willReturn(Optional.of(schedule))
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(emptyList())

        val result = requireNotNull(scheduleService.getWeeklySchedule(actorUserId, workplaceId, weekStartDate))

        assertThat(result.status).isEqualTo(ScheduleStatus.PUBLISHED)
        assertThat(result.publishedAt).isEqualTo(publishedAt)
        assertThat(result.shifts).isEmpty()
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        verify(scheduleRepository).findByWorkplace_IdAndWeekStartDate(workplaceId, weekStartDate)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)
    }

    @Test
    @DisplayName("SCH-06 - 유효한 DRAFT Schedule이면 공개에 성공한다")
    fun publishSchedulePublishesValidDraftSchedule() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val userId = 30L
        val memberId = 40L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val startAt = LocalDateTime.of(2026, 9, 21, 9, 0)
        val endAt = LocalDateTime.of(2026, 9, 21, 14, 0)
        val publishedAt = LocalDateTime.of(2026, 9, 19, 18, 30)
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()
        val user = mock<User>()
        given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user))
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.id).willReturn(scheduleId)
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.weekStartDate).willReturn(weekStartDate)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT, ScheduleStatus.PUBLISHED)
        given(schedule.publishedAt).willReturn(publishedAt)
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(listOf(shift))
        given(shift.member).willReturn(member)
        given(member.workplace).willReturn(workplace)
        given(member.leftAt).willReturn(null)
        given(member.user).willReturn(user)
        given(member.id).willReturn(memberId)
        given(user.id).willReturn(userId)
        given(shift.startAt).willReturn(startAt)
        given(shift.endAt).willReturn(endAt)
        given(clock.instant()).willReturn(Instant.parse("2026-09-19T09:30:00Z"))
        given(clock.zone).willReturn(ZoneId.of("Asia/Seoul"))

        val result = scheduleService.publishSchedule(actorUserId, workplaceId, scheduleId, false)

        assertThat(result.scheduleId).isEqualTo(scheduleId)
        assertThat(result.workplaceId).isEqualTo(workplaceId)
        assertThat(result.weekStartDate).isEqualTo(weekStartDate)
        assertThat(result.status).isEqualTo(ScheduleStatus.PUBLISHED)
        assertThat(result.publishedAt).isEqualTo(publishedAt)
        verify(scheduleRepository).findByIdForUpdate(scheduleId)
        verify(userRepository).findByIdForUpdate(userId)
        val eventCaptor = argumentCaptor<SchedulePublishedEvent>()
        val publishOrder = inOrder(schedule, applicationEventPublisher)
        publishOrder.verify(schedule).publish(publishedAt)
        publishOrder.verify(applicationEventPublisher).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.scheduleId).isEqualTo(scheduleId)
        assertThat(eventCaptor.firstValue.recipientMemberIds).containsExactly(memberId)
    }

    @Test
    @DisplayName("SCH-06 - 이미 PUBLISHED인 Schedule은 다시 공개할 수 없다")
    fun publishScheduleThrowsExceptionWhenAlreadyPublished() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val schedule = mock<Schedule>()
        val workplace = mock<Workplace>()
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)

        val exception = assertThrows<BusinessException> {
            scheduleService.publishSchedule(
                actorUserId,
                workplaceId,
                scheduleId,
                false,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_DRAFT)
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        verify(scheduleRepository).findByIdForUpdate(scheduleId)
        verify(schedule, never()).publish(any())
        verifyNoInteractions(shiftRepository, unavailableTimeRepository, clock)
    }

    @Test
    @DisplayName("SCH-06 - Shift가 없는 DRAFT Schedule은 공개할 수 없다")
    fun publishScheduleThrowsExceptionWhenDraftScheduleHasNoShifts() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val schedule = mock<Schedule>()
        val workplace = mock<Workplace>()
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(schedule.id).willReturn(scheduleId)
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(emptyList())

        val exception = assertThrows<BusinessException> {
            scheduleService.publishSchedule(
                actorUserId,
                workplaceId,
                scheduleId,
                false,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.EMPTY_SCHEDULE)
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        verify(scheduleRepository).findByIdForUpdate(scheduleId)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)
        verify(schedule, never()).publish(any())
        verifyNoInteractions(userRepository, unavailableTimeRepository, clock)
    }

    @Test
    @DisplayName("SCH-06 - 현재 구성원이 아닌 Shift 담당자가 있으면 공개할 수 없다")
    fun publishScheduleThrowsExceptionWhenShiftMemberHasLeft() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(schedule.id).willReturn(scheduleId)
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(listOf(shift))
        given(shift.member).willReturn(member)
        given(member.workplace).willReturn(workplace)
        given(member.leftAt).willReturn(LocalDateTime.of(2026, 9, 18, 10, 0))

        val exception = assertThrows<BusinessException> {
            scheduleService.publishSchedule(
                actorUserId,
                workplaceId,
                scheduleId,
                false,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.INVALID_SHIFT_MEMBER)
        verify(scheduleRepository).findByIdForUpdate(scheduleId)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)
        verify(schedule, never()).publish(any())
        verifyNoInteractions(userRepository, unavailableTimeRepository, clock)
    }

    @Test
    @DisplayName("SCH-06 - 다른 Workplace의 Shift 담당자가 있으면 공개할 수 없다")
    fun publishScheduleThrowsExceptionWhenShiftMemberBelongsToOtherWorkplace() {
        val actorUserId = 1L
        val workplaceId = 10L
        val otherWorkplaceId = 99L
        val scheduleId = 20L
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val scheduleWorkplace = mock<Workplace>()
        val otherWorkplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(scheduleWorkplace)
        given(scheduleWorkplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(schedule.id).willReturn(scheduleId)
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(listOf(shift))
        given(shift.member).willReturn(member)
        given(member.workplace).willReturn(otherWorkplace)
        given(otherWorkplace.id).willReturn(otherWorkplaceId)

        val exception = assertThrows<BusinessException> {
            scheduleService.publishSchedule(
                actorUserId,
                workplaceId,
                scheduleId,
                false,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.INVALID_SHIFT_MEMBER)
        verify(workplaceMemberService).requireManager(actorUserId, workplaceId)
        verify(scheduleRepository).findByIdForUpdate(scheduleId)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)
        verify(schedule, never()).publish(any())
        verifyNoInteractions(userRepository, unavailableTimeRepository, clock)
    }

    @Test
    @DisplayName("SCH-06 - Shift 시작 시각과 종료 시각이 같으면 공개할 수 없다")
    fun publishScheduleThrowsExceptionWhenShiftStartEqualsEnd() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val invalidAt = LocalDateTime.of(2026, 9, 21, 10, 0)
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(schedule.id).willReturn(scheduleId)
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(listOf(shift))
        given(shift.member).willReturn(member)
        given(member.workplace).willReturn(workplace)
        given(member.leftAt).willReturn(null)
        given(shift.startAt).willReturn(invalidAt)
        given(shift.endAt).willReturn(invalidAt)

        val exception = assertThrows<BusinessException> {
            scheduleService.publishSchedule(
                actorUserId,
                workplaceId,
                scheduleId,
                false,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.INVALID_SHIFT_TIME)
        verify(scheduleRepository).findByIdForUpdate(scheduleId)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)
        verify(schedule, never()).publish(any())
        verifyNoInteractions(userRepository, unavailableTimeRepository, clock)
    }

    @Test
    @DisplayName("SCH-06 - Schedule 주차 범위를 벗어난 Shift가 있으면 공개할 수 없다")
    fun publishScheduleThrowsExceptionWhenShiftIsOutsideScheduleWeek() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val startAt = LocalDateTime.of(2026, 9, 20, 10, 0)
        val endAt = LocalDateTime.of(2026, 9, 20, 14, 0)
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(schedule.id).willReturn(scheduleId)
        given(schedule.weekStartDate).willReturn(weekStartDate)
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(listOf(shift))
        given(shift.member).willReturn(member)
        given(member.workplace).willReturn(workplace)
        given(member.leftAt).willReturn(null)
        given(shift.startAt).willReturn(startAt)
        given(shift.endAt).willReturn(endAt)

        val exception = assertThrows<BusinessException> {
            scheduleService.publishSchedule(
                actorUserId,
                workplaceId,
                scheduleId,
                false,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.INVALID_SHIFT_WEEK)
        verify(scheduleRepository).findByIdForUpdate(scheduleId)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)
        verify(schedule, never()).publish(any())
        verifyNoInteractions(userRepository, unavailableTimeRepository, clock)
    }

    @Test
    @DisplayName("SCH-06 - 같은 Member의 Shift가 겹치면 공개할 수 없다")
    fun publishScheduleThrowsExceptionWhenSameMemberShiftsOverlap() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val memberId = 30L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val firstStartAt = LocalDateTime.of(2026, 9, 21, 9, 0)
        val firstEndAt = LocalDateTime.of(2026, 9, 21, 14, 0)
        val secondStartAt = LocalDateTime.of(2026, 9, 21, 13, 0)
        val secondEndAt = LocalDateTime.of(2026, 9, 21, 18, 0)
        val schedule = mock<Schedule>()
        val firstShift = mock<Shift>()
        val secondShift = mock<Shift>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        given(schedule.id).willReturn(scheduleId)
        given(schedule.weekStartDate).willReturn(weekStartDate)
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(listOf(firstShift, secondShift))
        given(firstShift.member).willReturn(member)
        given(secondShift.member).willReturn(member)
        given(member.id).willReturn(memberId)
        given(member.workplace).willReturn(workplace)
        given(member.leftAt).willReturn(null)
        given(firstShift.startAt).willReturn(firstStartAt)
        given(firstShift.endAt).willReturn(firstEndAt)
        given(secondShift.startAt).willReturn(secondStartAt)
        given(secondShift.endAt).willReturn(secondEndAt)

        val exception = assertThrows<BusinessException> {
            scheduleService.publishSchedule(
                actorUserId,
                workplaceId,
                scheduleId,
                false,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.SHIFT_OVERLAP)
        verify(scheduleRepository).findByIdForUpdate(scheduleId)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)
        verify(schedule, never()).publish(any())
        verifyNoInteractions(userRepository, unavailableTimeRepository, clock)
    }

    @Test
    @DisplayName("SCH-06 - 기존 공식 Shift와 충돌하면 공개할 수 없다")
    fun publishScheduleThrowsExceptionWhenOfficialShiftConflicts() {
        val fixture = officialConflictFixture()
        given(shiftRepository.existsOverlappingOfficialShift(fixture.userId, ScheduleStatus.PUBLISHED, fixture.startAt, fixture.endAt, null)).willReturn(true)

        val exception = assertThrows<BusinessException> {
            scheduleService.publishSchedule(
                fixture.actorUserId,
                fixture.workplaceId,
                fixture.scheduleId,
                false,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.OFFICIAL_SHIFT_CONFLICT)
        verify(scheduleRepository).findByIdForUpdate(fixture.scheduleId)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(fixture.scheduleId)
        verify(userRepository).findByIdForUpdate(fixture.userId)
        verify(shiftRepository).existsOverlappingOfficialShift(fixture.userId, ScheduleStatus.PUBLISHED, fixture.startAt, fixture.endAt, null)
        verify(fixture.schedule, never()).publish(any())
        verifyNoInteractions(unavailableTimeRepository, clock)
    }

    @Test
    @DisplayName("SCH-06 - UnavailableTime 충돌을 확인하지 않으면 공개할 수 없다")
    fun publishScheduleThrowsExceptionWhenUnavailableConflictIsNotConfirmed() {
        val fixture = officialConflictFixture()
        given(unavailableTimeRepository.existsOverlappingUnavailableTime(fixture.userId, fixture.startAt, fixture.endAt)).willReturn(true)

        val exception = assertThrows<BusinessException> {
            scheduleService.publishSchedule(
                fixture.actorUserId,
                fixture.workplaceId,
                fixture.scheduleId,
                false,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT)
        assertThat(exception.data).isInstanceOf(ConfirmationRequiredResponse::class.java)
        assertThat((exception.data as ConfirmationRequiredResponse).requiresConfirmation).isTrue()
        verify(scheduleRepository).findByIdForUpdate(fixture.scheduleId)
        verify(userRepository).findByIdForUpdate(fixture.userId)
        verify(shiftRepository).existsOverlappingOfficialShift(fixture.userId, ScheduleStatus.PUBLISHED, fixture.startAt, fixture.endAt, null)
        verify(unavailableTimeRepository).existsOverlappingUnavailableTime(fixture.userId, fixture.startAt, fixture.endAt)
        verify(fixture.schedule, never()).publish(any())
        verifyNoInteractions(clock, applicationEventPublisher)
    }

    @Test
    @DisplayName("SCH-06 - UnavailableTime 충돌을 확인하면 공개할 수 있다")
    fun publishScheduleSucceedsWhenUnavailableConflictIsConfirmed() {
        val fixture = officialConflictFixture(publishSucceeds = true)
        given(unavailableTimeRepository.existsOverlappingUnavailableTime(fixture.userId, fixture.startAt, fixture.endAt)).willReturn(true)

        val result = scheduleService.publishSchedule(fixture.actorUserId, fixture.workplaceId, fixture.scheduleId, true)

        assertThat(result.status).isEqualTo(ScheduleStatus.PUBLISHED)
        assertThat(result.publishedAt).isEqualTo(fixture.publishedAt)
        verify(scheduleRepository).findByIdForUpdate(fixture.scheduleId)
        verify(userRepository).findByIdForUpdate(fixture.userId)
        verify(shiftRepository).existsOverlappingOfficialShift(fixture.userId, ScheduleStatus.PUBLISHED, fixture.startAt, fixture.endAt, null)
        verify(unavailableTimeRepository).existsOverlappingUnavailableTime(fixture.userId, fixture.startAt, fixture.endAt)
        verify(fixture.schedule).publish(fixture.publishedAt)
    }

    @Test
    @DisplayName("SCH-06 - confirm=true여도 공식 Shift 충돌이 있으면 공개할 수 없다")
    fun publishScheduleThrowsExceptionWhenOfficialConflictExistsEvenIfConfirmed() {
        val fixture = officialConflictFixture()
        given(shiftRepository.existsOverlappingOfficialShift(fixture.userId, ScheduleStatus.PUBLISHED, fixture.startAt, fixture.endAt, null)).willReturn(true)

        val exception = assertThrows<BusinessException> {
            scheduleService.publishSchedule(
                fixture.actorUserId,
                fixture.workplaceId,
                fixture.scheduleId,
                true,
            )
        }

        assertThat(exception.errorCode).isEqualTo(ScheduleErrorCode.OFFICIAL_SHIFT_CONFLICT)
        verify(scheduleRepository).findByIdForUpdate(fixture.scheduleId)
        verify(shiftRepository).findBySchedule_IdOrderByStartAtAscIdAsc(fixture.scheduleId)
        verify(userRepository).findByIdForUpdate(fixture.userId)
        verify(shiftRepository).existsOverlappingOfficialShift(fixture.userId, ScheduleStatus.PUBLISHED, fixture.startAt, fixture.endAt, null)
        verify(fixture.schedule, never()).publish(any())
        verifyNoInteractions(unavailableTimeRepository, clock)
    }

    private data class OfficialConflictFixture(
        val actorUserId: Long,
        val workplaceId: Long,
        val scheduleId: Long,
        val userId: Long,
        val memberId: Long,
        val startAt: LocalDateTime,
        val endAt: LocalDateTime,
        val publishedAt: LocalDateTime,
        val schedule: Schedule,
    )

    private fun officialConflictFixture(publishSucceeds: Boolean = false): OfficialConflictFixture {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val userId = 30L
        val memberId = 40L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val startAt = LocalDateTime.of(2026, 9, 21, 10, 0)
        val endAt = LocalDateTime.of(2026, 9, 21, 14, 0)
        val publishedAt = LocalDateTime.of(2026, 9, 19, 18, 30)
        val schedule = mock<Schedule>()
        val shift = mock<Shift>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()
        val user = mock<User>()
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.id).willReturn(scheduleId)
        given(schedule.workplace).willReturn(workplace)
        given(schedule.weekStartDate).willReturn(weekStartDate)
        given(workplace.id).willReturn(workplaceId)
        if (publishSucceeds) {
            given(schedule.status).willReturn(ScheduleStatus.DRAFT, ScheduleStatus.PUBLISHED)
            given(schedule.publishedAt).willReturn(publishedAt)
            given(member.id).willReturn(memberId)
            given(clock.instant()).willReturn(Instant.parse("2026-09-19T09:30:00Z"))
            given(clock.zone).willReturn(ZoneId.of("Asia/Seoul"))
        } else {
            given(schedule.status).willReturn(ScheduleStatus.DRAFT)
        }
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(listOf(shift))
        given(shift.member).willReturn(member)
        given(shift.startAt).willReturn(startAt)
        given(shift.endAt).willReturn(endAt)
        given(member.workplace).willReturn(workplace)
        given(member.leftAt).willReturn(null)
        given(member.user).willReturn(user)
        given(user.id).willReturn(userId)
        given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user))
        return OfficialConflictFixture(actorUserId, workplaceId, scheduleId, userId, memberId, startAt, endAt, publishedAt, schedule)
    }

    @Test
    @DisplayName("SCH-06 - Shift 담당 User는 중복 없이 ID 오름차순으로 Lock한다")
    fun publishScheduleLocksShiftUsersInAscendingOrderWithoutDuplicates() {
        val actorUserId = 1L
        val workplaceId = 10L
        val scheduleId = 20L
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val publishedAt = LocalDateTime.of(2026, 9, 19, 18, 30)
        val schedule = mock<Schedule>()
        val workplace = mock<Workplace>()
        val user30 = mock<User>()
        val user10 = mock<User>()
        val user20 = mock<User>()
        val member30 = mock<WorkplaceMember>()
        val member10 = mock<WorkplaceMember>()
        val member20 = mock<WorkplaceMember>()
        val firstShift = mock<Shift>()
        val secondShift = mock<Shift>()
        val thirdShift = mock<Shift>()
        val fourthShift = mock<Shift>()
        val firstStartAt = LocalDateTime.of(2026, 9, 21, 9, 0)
        val firstEndAt = LocalDateTime.of(2026, 9, 21, 10, 0)
        val secondStartAt = LocalDateTime.of(2026, 9, 21, 10, 0)
        val secondEndAt = LocalDateTime.of(2026, 9, 21, 11, 0)
        val thirdStartAt = LocalDateTime.of(2026, 9, 21, 11, 0)
        val thirdEndAt = LocalDateTime.of(2026, 9, 21, 12, 0)
        val fourthStartAt = LocalDateTime.of(2026, 9, 21, 12, 0)
        val fourthEndAt = LocalDateTime.of(2026, 9, 21, 13, 0)
        given(scheduleRepository.findByIdForUpdate(scheduleId)).willReturn(Optional.of(schedule))
        given(schedule.id).willReturn(scheduleId)
        given(schedule.workplace).willReturn(workplace)
        given(workplace.id).willReturn(workplaceId)
        given(schedule.status).willReturn(ScheduleStatus.DRAFT, ScheduleStatus.PUBLISHED)
        given(schedule.weekStartDate).willReturn(weekStartDate)
        given(schedule.publishedAt).willReturn(publishedAt)
        given(user30.id).willReturn(30L)
        given(user10.id).willReturn(10L)
        given(user20.id).willReturn(20L)
        given(member30.id).willReturn(300L)
        given(member10.id).willReturn(100L)
        given(member20.id).willReturn(200L)
        given(member30.workplace).willReturn(workplace)
        given(member10.workplace).willReturn(workplace)
        given(member20.workplace).willReturn(workplace)
        given(member30.leftAt).willReturn(null)
        given(member10.leftAt).willReturn(null)
        given(member20.leftAt).willReturn(null)
        given(member30.user).willReturn(user30)
        given(member10.user).willReturn(user10)
        given(member20.user).willReturn(user20)
        given(firstShift.member).willReturn(member30)
        given(secondShift.member).willReturn(member10)
        given(thirdShift.member).willReturn(member30)
        given(fourthShift.member).willReturn(member20)
        given(firstShift.startAt).willReturn(firstStartAt)
        given(firstShift.endAt).willReturn(firstEndAt)
        given(secondShift.startAt).willReturn(secondStartAt)
        given(secondShift.endAt).willReturn(secondEndAt)
        given(thirdShift.startAt).willReturn(thirdStartAt)
        given(thirdShift.endAt).willReturn(thirdEndAt)
        given(fourthShift.startAt).willReturn(fourthStartAt)
        given(fourthShift.endAt).willReturn(fourthEndAt)
        given(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(scheduleId)).willReturn(listOf(firstShift, secondShift, thirdShift, fourthShift))
        given(userRepository.findByIdForUpdate(10L)).willReturn(Optional.of(user10))
        given(userRepository.findByIdForUpdate(20L)).willReturn(Optional.of(user20))
        given(userRepository.findByIdForUpdate(30L)).willReturn(Optional.of(user30))
        given(clock.instant()).willReturn(Instant.parse("2026-09-19T09:30:00Z"))
        given(clock.zone).willReturn(ZoneId.of("Asia/Seoul"))

        scheduleService.publishSchedule(actorUserId, workplaceId, scheduleId, false)

        val userLockOrder = inOrder(userRepository)
        userLockOrder.verify(userRepository).findByIdForUpdate(10L)
        userLockOrder.verify(userRepository).findByIdForUpdate(20L)
        userLockOrder.verify(userRepository).findByIdForUpdate(30L)
        userLockOrder.verifyNoMoreInteractions()
        verify(schedule).publish(publishedAt)
        val eventCaptor = argumentCaptor<SchedulePublishedEvent>()
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.recipientMemberIds).containsExactly(100L, 200L, 300L)
    }
}
