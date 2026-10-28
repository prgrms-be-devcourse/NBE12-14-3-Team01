package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.UnavailableTime
import com.merge.backend.domain.shift.exception.UnavailableTimeErrorCode
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.rq.Rq
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InOrder
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class)
class UnavailableTimeServiceTest {


   @Mock private lateinit var user: User

   @Mock private lateinit var unavailableTimeRepository: UnavailableTimeRepository

   @Mock private lateinit var shiftRepository: ShiftRepository

   @Mock private lateinit var rq: Rq

   @Mock private lateinit var clock: Clock

   @Mock private lateinit var userService: UserService

   @InjectMocks private lateinit var unavailableTimeService: UnavailableTimeService


   @BeforeEach

   fun setUp() {

       lenient().`when`(clock.instant()).thenReturn(Instant.parse("2026-09-21T06:00:00Z"))

       lenient().`when`(clock.zone).thenReturn(ZoneId.of("Asia/Seoul"))

   }


   @Test
   @DisplayName("UNA-01 - 유효한 미래 시간이면 근무 불가능 일정을 등록한다")

   fun registerSucceedsWithValidFutureTime() {

       val startAt = LocalDateTime.of(2026, 9, 22, 10, 0)

       val endAt = LocalDateTime.of(2026, 9, 22, 12, 0)

       val user = mock<User>()

       given(rq.actorId).willReturn(USER_ID)

       given(userService.getByIdForUpdate(USER_ID)).willReturn(user)

       given(unavailableTimeRepository.existsOverlappingUnavailableTime(USER_ID, startAt, endAt)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, startAt, endAt, null)).willReturn(false)

       given(unavailableTimeRepository.save(any<UnavailableTime>())).willAnswer { it.getArgument<UnavailableTime>(0) }


       val result = unavailableTimeService.register(startAt, endAt, false)


       assertThat(result).isNotNull

       assertThat(result.user).isEqualTo(user)

       assertThat(result.startAt).isEqualTo(startAt)

       assertThat(result.endAt).isEqualTo(endAt)

       val callOrder: InOrder = inOrder(userService, unavailableTimeRepository, shiftRepository)

       callOrder.verify(userService).getByIdForUpdate(USER_ID)

       callOrder.verify(unavailableTimeRepository).existsOverlappingUnavailableTime(USER_ID, startAt, endAt)

       callOrder.verify(shiftRepository).existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, startAt, endAt, null)

       callOrder.verify(unavailableTimeRepository).save(any<UnavailableTime>())

   }


   @Test

   @DisplayName("UNA-01 - 시작 시간과 종료 시간이 같으면 등록할 수 없다")

   fun registerThrowsExceptionWhenStartEqualsEnd() {

       val sameTime = LocalDateTime.of(2026, 9, 22, 10, 0)

       mockRegisterUser()


       assertError(UnavailableTimeErrorCode.INVALID_TIME_RANGE) { unavailableTimeService.register(sameTime, sameTime, false) }

       verify(unavailableTimeRepository, never()).save(any<UnavailableTime>())

   }


   @Test

   @DisplayName("UNA-01 - 시작 시간이 종료 시간보다 늦으면 등록할 수 없다")

   fun registerThrowsExceptionWhenStartIsAfterEnd() {

       val startAt = LocalDateTime.of(2026, 9, 22, 12, 0)

       val endAt = LocalDateTime.of(2026, 9, 22, 10, 0)

       mockRegisterUser()


       assertError(UnavailableTimeErrorCode.INVALID_TIME_RANGE) { unavailableTimeService.register(startAt, endAt, false) }

       verify(unavailableTimeRepository, never()).save(any<UnavailableTime>())

   }


   @Test
   @DisplayName("UNA-01 - 시작 시간이 현재 시간과 같으면 등록할 수 없다")

   fun registerThrowsExceptionWhenStartEqualsNow() {

       mockRegisterUser()

       assertError(UnavailableTimeErrorCode.NOT_FUTURE_TIME) { unavailableTimeService.register(NOW, NOW.plusHours(1), false) }

       verify(unavailableTimeRepository, never()).save(any<UnavailableTime>())

   }


   @Test
   @DisplayName("UNA-01 - 시작 시간이 현재 시간보다 이전이면 등록할 수 없다")

   fun registerThrowsExceptionWhenStartIsPast() {

       mockRegisterUser()

       assertError(UnavailableTimeErrorCode.NOT_FUTURE_TIME) { unavailableTimeService.register(NOW.minusMinutes(1), NOW.plusHours(1), false) }

       verify(unavailableTimeRepository, never()).save(any<UnavailableTime>())

   }


   @Test
   @DisplayName("UNA-01 - 새 일정의 앞부분이 기존 일정과 겹치면 등록할 수 없다")

   fun registerThrowsExceptionWhenFrontPartOverlaps() = assertRegisterOverlap(LocalDateTime.of(2026, 9, 22, 9, 0), LocalDateTime.of(2026, 9, 22, 11, 0))

   @Test
   @DisplayName("UNA-01 - 새 일정의 뒷부분이 기존 일정과 겹치면 등록할 수 없다")

   fun registerThrowsExceptionWhenBackPartOverlaps() = assertRegisterOverlap(LocalDateTime.of(2026, 9, 22, 9, 0), LocalDateTime.of(2026, 9, 22, 11, 0))

   @Test
   @DisplayName("UNA-01 - 새 일정이 기존 일정 안에 포함되면 등록할 수 없다")

   fun registerThrowsExceptionWhenNewTimeIsInsideExistingTime() = assertRegisterOverlap(LocalDateTime.of(2026, 9, 22, 9, 0), LocalDateTime.of(2026, 9, 22, 11, 0))

   @Test
   @DisplayName("UNA-01 - 새 일정이 기존 일정을 포함하면 등록할 수 없다")

   fun registerThrowsExceptionWhenNewTimeContainsExistingTime() = assertRegisterOverlap(LocalDateTime.of(2026, 9, 22, 9, 0), LocalDateTime.of(2026, 9, 22, 11, 0))


   @Test
   @DisplayName("UNA-01 - 새 일정 종료와 기존 일정 시작이 같으면 등록할 수 있다")

   fun registerSucceedsWhenNewEndEqualsExistingStart() = assertRegisterAdjacentSuccess(LocalDateTime.of(2026, 9, 22, 10, 0), LocalDateTime.of(2026, 9, 22, 12, 0))

   @Test
   @DisplayName("UNA-01 - 기존 일정 종료와 새 일정 시작이 같으면 등록할 수 있다")

   fun registerSucceedsWhenNewStartEqualsExistingEnd() = assertRegisterAdjacentSuccess(LocalDateTime.of(2026, 9, 22, 12, 0), LocalDateTime.of(2026, 9, 22, 14, 0))


   @Test
   @DisplayName("UNA-01 - 공식 Shift와 충돌하고 확인하지 않으면 등록할 수 없다")

   fun registerThrowsExceptionWhenOfficialShiftConflictIsNotConfirmed() {

       val startAt = LocalDateTime.of(2026, 9, 22, 10, 0)

       val endAt = LocalDateTime.of(2026, 9, 22, 12, 0)

       mockRegisterUser()

       given(unavailableTimeRepository.existsOverlappingUnavailableTime(USER_ID, startAt, endAt)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, startAt, endAt, null)).willReturn(true)

       assertError(UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT) { unavailableTimeService.register(startAt, endAt, false) }

       verify(unavailableTimeRepository, never()).save(any<UnavailableTime>())

   }


   @Test
   @DisplayName("UNA-01 - 공식 Shift와 충돌해도 확인하면 등록할 수 있다")

   fun registerSucceedsWhenOfficialShiftConflictIsConfirmed() {

       val startAt = LocalDateTime.of(2026, 9, 22, 10, 0)

       val endAt = LocalDateTime.of(2026, 9, 22, 12, 0)

       val user = mockRegisterUser()

       given(unavailableTimeRepository.existsOverlappingUnavailableTime(USER_ID, startAt, endAt)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, startAt, endAt, null)).willReturn(true)

       given(unavailableTimeRepository.save(any<UnavailableTime>())).willAnswer { it.getArgument<UnavailableTime>(0) }

       val result = unavailableTimeService.register(startAt, endAt, true)

       assertThat(result.user).isEqualTo(user)

       assertThat(result.startAt).isEqualTo(startAt)

       assertThat(result.endAt).isEqualTo(endAt)

       verify(unavailableTimeRepository).save(any<UnavailableTime>())

   }


   @Test
   @DisplayName("UNA-02 - 미래 근무 불가능 일정을 조회한다")

   fun findAllReturnsFutureUnavailableTimes() {

       val startAt = LocalDateTime.of(2026, 9, 22, 10, 0)

       val endAt = LocalDateTime.of(2026, 9, 22, 12, 0)

       val unavailableTime = mock<UnavailableTime>()

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findByUserIdAndEndAtAfterOrderByStartAtAsc(USER_ID, NOW)).willReturn(listOf(unavailableTime))

       given(unavailableTime.id).willReturn(10L)

       given(unavailableTime.startAt).willReturn(startAt)

       given(unavailableTime.endAt).willReturn(endAt)

       given(unavailableTimeRepository.findIdsWithOfficialShiftConflict(USER_ID, NOW)).willReturn(emptyList())

       val result = unavailableTimeService.findAll()

       assertThat(result).hasSize(1)

       assertThat(result[0].unavailableTimeId).isEqualTo(10L)

       assertThat(result[0].startAt).isEqualTo(startAt)

       assertThat(result[0].endAt).isEqualTo(endAt)

       assertThat(result[0].officialShiftConflict).isFalse()

       verify(unavailableTimeRepository).findIdsWithOfficialShiftConflict(USER_ID, NOW)

       verify(shiftRepository, never()).existsOverlappingOfficialShift(anyOrNull(), any(), any(), any(), anyOrNull())

   }


   @Test
   @DisplayName("UNA-02 - 공식 Shift와 겹치면 officialShiftConflict가 true다")

   fun findAllReturnsOfficialShiftConflictTrue() {

       val startAt = LocalDateTime.of(2026, 9, 22, 10, 0)

       val endAt = LocalDateTime.of(2026, 9, 22, 12, 0)

       val unavailableTime = mock<UnavailableTime>()

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findByUserIdAndEndAtAfterOrderByStartAtAsc(USER_ID, NOW)).willReturn(listOf(unavailableTime))

       given(unavailableTime.id).willReturn(10L)

       given(unavailableTime.startAt).willReturn(startAt)

       given(unavailableTime.endAt).willReturn(endAt)

       given(unavailableTimeRepository.findIdsWithOfficialShiftConflict(USER_ID, NOW)).willReturn(listOf(10L))

       val result = unavailableTimeService.findAll()

       assertThat(result).hasSize(1)

       assertThat(result[0].officialShiftConflict).isTrue()

       verify(unavailableTimeRepository).findIdsWithOfficialShiftConflict(USER_ID, NOW)

   }


   @Test
   @DisplayName("UNA-02 - 조회할 일정이 없으면 빈 리스트를 반환한다")

   fun findAllReturnsEmptyListWhenNoUnavailableTimeExists() {

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findByUserIdAndEndAtAfterOrderByStartAtAsc(USER_ID, NOW)).willReturn(emptyList())

       val result = unavailableTimeService.findAll()

       assertThat(result).isEmpty()

       verify(unavailableTimeRepository).findByUserIdAndEndAtAfterOrderByStartAtAsc(USER_ID, NOW)

       verify(unavailableTimeRepository, never()).findIdsWithOfficialShiftConflict(anyOrNull(), any())

   }


   @Test
   @DisplayName("UNA-03 - 본인의 미래 일정을 정상적으로 수정한다")

   fun updateSucceedsWithValidRequest() {

       val id = 10L

       val oldStart = LocalDateTime.of(2026, 9, 22, 10, 0)

       val newStart = LocalDateTime.of(2026, 9, 22, 13, 0)

       val newEnd = LocalDateTime.of(2026, 9, 22, 15, 0)

       val user = mockUser(USER_ID)

       val unavailableTime = mock<UnavailableTime>()

       given(unavailableTime.user).willReturn(user)

       given(unavailableTime.startAt).willReturn(oldStart)

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findById(id)).willReturn(Optional.of(unavailableTime))

       given(unavailableTimeRepository.existsOverlappingUnavailableTimeExcludingId(USER_ID, id, newStart, newEnd)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, newStart, newEnd, null)).willReturn(false)

       val result = unavailableTimeService.update(id, newStart, newEnd, false)

       assertThat(result).isEqualTo(unavailableTime)

       verify(unavailableTime).update(newStart, newEnd)

       val order = inOrder(userService, unavailableTimeRepository, shiftRepository, unavailableTime)

       order.verify(userService).getByIdForUpdate(USER_ID)

       order.verify(unavailableTimeRepository).findById(id)

       order.verify(unavailableTimeRepository).existsOverlappingUnavailableTimeExcludingId(USER_ID, id, newStart, newEnd)

       order.verify(shiftRepository).existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, newStart, newEnd, null)

       order.verify(unavailableTime).update(newStart, newEnd)

   }


   @Test
   @DisplayName("UNA-03 - 존재하지 않는 일정은 수정할 수 없다")

   fun updateThrowsExceptionWhenUnavailableTimeDoesNotExist() {

       val id = 999L



       given(rq.actorId).willReturn(USER_ID)



       given(unavailableTimeRepository.findById(id)).willReturn(Optional.empty())

       assertError(UnavailableTimeErrorCode.NOT_FOUND) { unavailableTimeService.update(id, NOW.plusHours(1), NOW.plusHours(2), false) }

   }


   @Test
   @DisplayName("UNA-03 - 다른 사용자의 일정은 수정할 수 없다")

   fun updateThrowsExceptionWhenUserIsNotOwner() {

       val id = 10L

       val unavailableTime = mock<UnavailableTime>()

       val otherUser = mockUser(999L)

       given(unavailableTime.user).willReturn(otherUser)

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findById(id)).willReturn(Optional.of(unavailableTime))

       assertError(UnavailableTimeErrorCode.FORBIDDEN) { unavailableTimeService.update(id, NOW.plusHours(1), NOW.plusHours(2), false) }

       verify(unavailableTime, never()).update(any(), any())

   }


   @Test
   @DisplayName("UNA-03 - 기존 일정 시작 시각이 현재 시각과 같으면 수정할 수 없다")

   fun updateThrowsExceptionWhenExistingStartEqualsNow() = assertExistingNotModifiable(NOW)

   @Test
   @DisplayName("UNA-03 - 기존 일정이 이미 시작되었으면 수정할 수 없다")

   fun updateThrowsExceptionWhenExistingStartIsPast() = assertExistingNotModifiable(NOW.minusMinutes(1))


   @Test
   @DisplayName("UNA-03 - 새 시작 시간과 종료 시간이 같으면 수정할 수 없다")

   fun updateThrowsExceptionWhenNewStartEqualsEnd() {

       val id = 10L

       val same = NOW.plusHours(2)

       val unavailableTime = mockOwnedFutureUnavailableTime(id, NOW.plusDays(1))

       assertError(UnavailableTimeErrorCode.INVALID_TIME_RANGE) { unavailableTimeService.update(id, same, same, false) }

       verify(unavailableTime, never()).update(any(), any())

   }

   @Test
   @DisplayName("UNA-03 - 새 시작 시간이 종료 시간보다 늦으면 수정할 수 없다")

   fun updateThrowsExceptionWhenNewStartIsAfterEnd() {

       val id = 10L

       val unavailableTime = mockOwnedFutureUnavailableTime(id, NOW.plusDays(1))

       assertError(UnavailableTimeErrorCode.INVALID_TIME_RANGE) { unavailableTimeService.update(id, NOW.plusHours(3), NOW.plusHours(2), false) }

       verify(unavailableTime, never()).update(any(), any())

   }

   @Test
   @DisplayName("UNA-03 - 새 시작 시간이 현재 시간과 같으면 수정할 수 없다")

   fun updateThrowsExceptionWhenNewStartEqualsNow() {

       val id = 10L

       val unavailableTime = mockOwnedFutureUnavailableTime(id, NOW.plusDays(1))

       assertError(UnavailableTimeErrorCode.NOT_FUTURE_TIME) { unavailableTimeService.update(id, NOW, NOW.plusHours(1), false) }

       verify(unavailableTime, never()).update(any(), any())

   }

   @Test
   @DisplayName("UNA-03 - 새 시작 시간이 과거이면 수정할 수 없다")

   fun updateThrowsExceptionWhenNewStartIsPast() {

       val id = 10L

       val unavailableTime = mockOwnedFutureUnavailableTime(id, NOW.plusDays(1))

       assertError(UnavailableTimeErrorCode.NOT_FUTURE_TIME) { unavailableTimeService.update(id, NOW.minusMinutes(1), NOW.plusHours(1), false) }

       verify(unavailableTime, never()).update(any(), any())

   }


   @Test
   @DisplayName("UNA-03 - 수정 시 자기 자신은 중복 검사에서 제외한다")

   fun updateExcludesItselfFromOverlapValidation() {

       val id = 10L

       val start = LocalDateTime.of(2026, 9, 22, 11, 0)

       val end = LocalDateTime.of(2026, 9, 22, 13, 0)

       val unavailableTime = mockOwnedFutureUnavailableTime(id, LocalDateTime.of(2026, 9, 22, 10, 0))

       given(unavailableTimeRepository.existsOverlappingUnavailableTimeExcludingId(USER_ID, id, start, end)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, start, end, null)).willReturn(false)

       val result = unavailableTimeService.update(id, start, end, false)

       assertThat(result).isEqualTo(unavailableTime)

       verify(unavailableTimeRepository).existsOverlappingUnavailableTimeExcludingId(USER_ID, id, start, end)

       verify(unavailableTime).update(start, end)

   }


   @Test
   @DisplayName("UNA-03 - 다른 근무 불가능 일정과 겹치면 수정할 수 없다")

   fun updateThrowsExceptionWhenAnotherUnavailableTimeOverlaps() {

       val id = 10L

       val start = LocalDateTime.of(2026, 9, 22, 11, 0)

       val end = LocalDateTime.of(2026, 9, 22, 13, 0)

       val target = mockOwnedFutureUnavailableTime(id, LocalDateTime.of(2026, 9, 23, 10, 0))

       given(unavailableTimeRepository.existsOverlappingUnavailableTimeExcludingId(USER_ID, id, start, end)).willReturn(true)

       assertError(UnavailableTimeErrorCode.TIME_OVERLAP) { unavailableTimeService.update(id, start, end, false) }

       verify(unavailableTimeRepository).existsOverlappingUnavailableTimeExcludingId(USER_ID, id, start, end)

       verify(target, never()).update(any(), any())

   }


   @Test
   @DisplayName("UNA-03 - 공식 Shift와 충돌하고 확인하지 않으면 수정할 수 없다")

   fun updateThrowsExceptionWhenOfficialShiftConflictIsNotConfirmed() {

       val id = 10L

       val start = LocalDateTime.of(2026, 9, 22, 10, 0)

       val end = LocalDateTime.of(2026, 9, 22, 12, 0)

       val unavailableTime = mockOwnedFutureUnavailableTime(id, LocalDateTime.of(2026, 9, 23, 10, 0))

       given(unavailableTimeRepository.existsOverlappingUnavailableTimeExcludingId(USER_ID, id, start, end)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, start, end, null)).willReturn(true)

       assertError(UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT) { unavailableTimeService.update(id, start, end, false) }

       verify(unavailableTimeRepository).existsOverlappingUnavailableTimeExcludingId(USER_ID, id, start, end)

       verify(unavailableTime, never()).update(any(), any())

   }


   @Test
   @DisplayName("UNA-03 - 공식 Shift와 충돌해도 확인하면 수정할 수 있다")

   fun updateSucceedsWhenOfficialShiftConflictIsConfirmed() {

       val id = 10L

       val start = LocalDateTime.of(2026, 9, 22, 10, 0)

       val end = LocalDateTime.of(2026, 9, 22, 12, 0)

       val unavailableTime = mockOwnedFutureUnavailableTime(id, LocalDateTime.of(2026, 9, 23, 10, 0))

       given(unavailableTimeRepository.existsOverlappingUnavailableTimeExcludingId(USER_ID, id, start, end)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, start, end, null)).willReturn(true)

       val result = unavailableTimeService.update(id, start, end, true)

       assertThat(result).isEqualTo(unavailableTime)

       verify(unavailableTime).update(start, end)

   }


   @Test
   @DisplayName("UNA-04 - 본인의 근무 불가능 일정을 삭제한다")

   fun deleteSucceedsWhenUserIsOwner() {

       val id = 10L

       val unavailableTime = mock<UnavailableTime>()

       val user = mockUser(USER_ID)

       given(unavailableTime.user).willReturn(user)

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findById(id)).willReturn(Optional.of(unavailableTime))

       unavailableTimeService.delete(id)

       verify(unavailableTimeRepository).delete(unavailableTime)

   }

   @Test
   @DisplayName("UNA-04 - 존재하지 않는 일정은 삭제할 수 없다")

   fun deleteThrowsExceptionWhenUnavailableTimeDoesNotExist() {

       val id = 999L

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findById(id)).willReturn(Optional.empty())

       assertError(UnavailableTimeErrorCode.NOT_FOUND) { unavailableTimeService.delete(id) }

       verify(unavailableTimeRepository, never()).delete(any())

   }

   @Test
   @DisplayName("UNA-04 - 다른 사용자의 일정은 삭제할 수 없다")

   fun deleteThrowsExceptionWhenUserIsNotOwner() {

       val id = 10L

       val unavailableTime = mock<UnavailableTime>()

       val otherUser = mockUser(999L)

       given(unavailableTime.user).willReturn(otherUser)

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findById(id)).willReturn(Optional.of(unavailableTime))

       assertError(UnavailableTimeErrorCode.FORBIDDEN) { unavailableTimeService.delete(id) }

       verify(unavailableTimeRepository, never()).delete(any())

   }

   @Test
   @DisplayName("UNA-04 - 삭제 시 시간 검증 없이 본인 일정은 삭제할 수 있다")

   fun deleteDoesNotValidateTime() {

       val id = 10L

       val unavailableTime = mock<UnavailableTime>()

       val user = mockUser(USER_ID)

       given(unavailableTime.user).willReturn(user)

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findById(id)).willReturn(Optional.of(unavailableTime))

       unavailableTimeService.delete(id)

       verify(unavailableTimeRepository).delete(unavailableTime)

       verify(unavailableTime, never()).startAt

       verify(unavailableTime, never()).endAt

       verify(shiftRepository, never()).existsOverlappingOfficialShift(anyOrNull(), any(), any(), any(), anyOrNull())

   }


   @Test
   @DisplayName("UNA-01: 공식 Shift와 충돌하고 확인하지 않으면 Warning 예외가 발생한다")

   fun register_officialShiftConflict_withoutConfirmation_throwsWarning() = assertRegisterWarning(false)

   @Test
   @DisplayName("UNA-01: 공식 Shift와 충돌하고 확인값이 null이면 Warning 예외가 발생한다")

   fun register_officialShiftConflict_withNullConfirmation_throwsWarning() = assertRegisterWarning(null, true)

   @Test
   @DisplayName("UNA-01: 공식 Shift와 충돌해도 사용자가 확인하면 등록할 수 있다")

   fun register_officialShiftConflict_withConfirmation_success() {

       val start = NOW.plusDays(1).withHour(10)

       val end = NOW.plusDays(1).withHour(12)

       given(rq.actorId).willReturn(USER_ID)

       given(userService.getByIdForUpdate(USER_ID)).willReturn(user)

       given(unavailableTimeRepository.existsOverlappingUnavailableTime(USER_ID, start, end)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, start, end, null)).willReturn(true)

       given(unavailableTimeRepository.save(any<UnavailableTime>())).willAnswer { it.getArgument<UnavailableTime>(0) }

       val result = unavailableTimeService.register(start, end, true)

       assertThat(result.startAt).isEqualTo(start)

       assertThat(result.endAt).isEqualTo(end)

       verify(unavailableTimeRepository).save(any<UnavailableTime>())

   }


   @Test
   @DisplayName("UNA-03: 수정 시간이 공식 Shift와 충돌하고 확인하지 않으면 Warning 예외가 발생한다")

   fun update_officialShiftConflict_withoutConfirmation_throwsWarning() {

       val id = 1L

       val existingStart = NOW.plusDays(1).withHour(9)

       val existingEnd = NOW.plusDays(1).withHour(11)

       val start = NOW.plusDays(2).withHour(10)

       val end = NOW.plusDays(2).withHour(12)

       val unavailableTime = UnavailableTime(user, existingStart, existingEnd)

       given(user.id).willReturn(USER_ID)

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findById(id)).willReturn(Optional.of(unavailableTime))

       given(unavailableTimeRepository.existsOverlappingUnavailableTimeExcludingId(USER_ID, id, start, end)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, start, end, null)).willReturn(true)

       val exception = assertThrows<BusinessException> { unavailableTimeService.update(id, start, end, false) }

       assertWarning(exception)

       assertThat(unavailableTime.startAt).isEqualTo(existingStart)

       assertThat(unavailableTime.endAt).isEqualTo(existingEnd)

   }


   @Test
   @DisplayName("UNA-03: 수정 시간이 공식 Shift와 충돌해도 사용자가 확인하면 수정할 수 있다")

   fun update_officialShiftConflict_withConfirmation_success() {

       val id = 1L

       val existingStart = NOW.plusDays(1).withHour(9)

       val existingEnd = NOW.plusDays(1).withHour(11)

       val start = NOW.plusDays(2).withHour(10)

       val end = NOW.plusDays(2).withHour(12)

       val unavailableTime = UnavailableTime(user, existingStart, existingEnd)

       given(user.id).willReturn(USER_ID)

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findById(id)).willReturn(Optional.of(unavailableTime))

       given(unavailableTimeRepository.existsOverlappingUnavailableTimeExcludingId(USER_ID, id, start, end)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, start, end, null)).willReturn(true)

       val result = unavailableTimeService.update(id, start, end, true)

       assertThat(result.startAt).isEqualTo(start)

       assertThat(result.endAt).isEqualTo(end)

   }


   private fun mockRegisterUser(): User = mock<User>().also { registeredUser ->

       given(rq.actorId).willReturn(USER_ID)

       given(userService.getByIdForUpdate(USER_ID)).willReturn(registeredUser)

   }

   private fun mockUser(userId: Long): User = mock<User>().also { given(it.id).willReturn(userId) }

   private fun mockOwnedFutureUnavailableTime(id: Long, existingStart: LocalDateTime): UnavailableTime {

       val unavailableTime = mock<UnavailableTime>()

       lenient().`when`(unavailableTime.id).thenReturn(id)

       val user = mockUser(USER_ID)

       given(unavailableTime.user).willReturn(user)

       given(unavailableTime.startAt).willReturn(existingStart)

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findById(id)).willReturn(Optional.of(unavailableTime))

       return unavailableTime

   }

   private fun assertRegisterOverlap(start: LocalDateTime, end: LocalDateTime) {

       mockRegisterUser()

       given(unavailableTimeRepository.existsOverlappingUnavailableTime(USER_ID, start, end)).willReturn(true)

       assertError(UnavailableTimeErrorCode.TIME_OVERLAP) { unavailableTimeService.register(start, end, false) }

       verify(unavailableTimeRepository).existsOverlappingUnavailableTime(USER_ID, start, end)

       verify(unavailableTimeRepository, never()).save(any())

   }

   private fun assertRegisterAdjacentSuccess(start: LocalDateTime, end: LocalDateTime) {

       mockRegisterUser()

       given(unavailableTimeRepository.existsOverlappingUnavailableTime(USER_ID, start, end)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, start, end, null)).willReturn(false)

       given(unavailableTimeRepository.save(any<UnavailableTime>())).willAnswer { it.getArgument<UnavailableTime>(0) }

       val result = unavailableTimeService.register(start, end, false)

       assertThat(result.startAt).isEqualTo(start)

       assertThat(result.endAt).isEqualTo(end)

       verify(unavailableTimeRepository).existsOverlappingUnavailableTime(USER_ID, start, end)

       verify(unavailableTimeRepository).save(any<UnavailableTime>())

   }

   private fun assertExistingNotModifiable(existingStart: LocalDateTime) {

       val id = 10L

       val unavailableTime = mock<UnavailableTime>()

       val user = mockUser(USER_ID)

       given(unavailableTime.user).willReturn(user)

       given(unavailableTime.startAt).willReturn(existingStart)

       given(rq.actorId).willReturn(USER_ID)

       given(unavailableTimeRepository.findById(id)).willReturn(Optional.of(unavailableTime))

       assertError(UnavailableTimeErrorCode.NOT_MODIFIABLE_TIME) { unavailableTimeService.update(id, NOW.plusHours(1), NOW.plusHours(2), false) }

       verify(unavailableTime, never()).update(any(), any())

   }

   private fun assertRegisterWarning(confirmation: Boolean?, verifyShift: Boolean = false) {

       val start = NOW.plusDays(1).withHour(10)

       val end = NOW.plusDays(1).withHour(12)

       given(rq.actorId).willReturn(USER_ID)

       given(userService.getByIdForUpdate(USER_ID)).willReturn(user)

       given(unavailableTimeRepository.existsOverlappingUnavailableTime(USER_ID, start, end)).willReturn(false)

       given(shiftRepository.existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, start, end, null)).willReturn(true)

       val exception = assertThrows<BusinessException> { unavailableTimeService.register(start, end, confirmation) }

       assertWarning(exception)

       if (verifyShift) verify(shiftRepository).existsOverlappingOfficialShift(USER_ID, ScheduleStatus.PUBLISHED, start, end, null)

       verify(unavailableTimeRepository, never()).save(any<UnavailableTime>())

   }

   private fun assertWarning(exception: BusinessException) {

       assertThat(exception.errorCode).isEqualTo(UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT)

       assertThat(exception.data).isInstanceOf(ConfirmationRequiredResponse::class.java)

       assertThat((exception.data as ConfirmationRequiredResponse).requiresConfirmation).isTrue()

   }

   private fun assertError(errorCode: UnavailableTimeErrorCode, action: () -> Unit): BusinessException = assertThrows<BusinessException>(action).also { assertThat(it.errorCode).isEqualTo(errorCode) }


   private companion object {

       const val USER_ID = 1L

       val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 21, 15, 0)

   }
}
