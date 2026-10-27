package com.merge.backend.domain.shift.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.merge.backend.domain.shift.entity.ScheduleStatus;
import com.merge.backend.domain.shift.entity.UnavailableTime;
import com.merge.backend.domain.shift.exception.UnavailableTimeErrorCode;
import com.merge.backend.domain.shift.repository.ShiftRepository;
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.user.service.UserService;
import com.merge.backend.global.dto.ConfirmationRequiredResponse;
import com.merge.backend.global.exception.BusinessException;
import com.merge.backend.global.rq.Rq;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnavailableTimeServiceTest {

    @Mock
    private User user;

    @Mock
    private UnavailableTimeRepository unavailableTimeRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private Rq rq;

    @Mock
    private Clock clock;

    @Mock
    private UserService userService;

    @InjectMocks
    private UnavailableTimeService unavailableTimeService;

    private static final Long USER_ID = 1L;

    private static final LocalDateTime NOW =
        LocalDateTime.of(2026, 9, 21, 15, 0);

    @BeforeEach
    void setUp() {
        lenient()
            .when(clock.instant())
            .thenReturn(
                Instant.parse("2026-09-21T06:00:00Z")
            );

        lenient()
            .when(clock.getZone())
            .thenReturn(
                ZoneId.of("Asia/Seoul")
            );
    }

    // =========================================================
    // UNA-01 등록
    // =========================================================

    @Test
    @DisplayName("UNA-01 - 유효한 미래 시간이면 근무 불가능 일정을 등록한다")
    void registerSucceedsWithValidFutureTime() {

        // given
        LocalDateTime startAt =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        LocalDateTime endAt =
            LocalDateTime.of(2026, 9, 22, 12, 0);

        User user = org.mockito.Mockito.mock(User.class);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(userService.getByIdForUpdate(USER_ID))
            .thenReturn(user);

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTime(
                    USER_ID,
                    startAt,
                    endAt
                )
        ).thenReturn(false);

        when(
            shiftRepository.existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                startAt,
                endAt,
                null
            )
        ).thenReturn(false);

        when(unavailableTimeRepository.save(any(UnavailableTime.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UnavailableTime result =
            unavailableTimeService.register(
                startAt,
                endAt,
                false
            );

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getStartAt()).isEqualTo(startAt);
        assertThat(result.getEndAt()).isEqualTo(endAt);

        InOrder callOrder =
            inOrder(
                userService,
                unavailableTimeRepository,
                shiftRepository
            );

        callOrder.verify(userService)
            .getByIdForUpdate(USER_ID);

        callOrder.verify(unavailableTimeRepository)
            .existsOverlappingUnavailableTime(
                USER_ID,
                startAt,
                endAt
            );

        callOrder.verify(shiftRepository)
            .existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                startAt,
                endAt,
                null
            );

        callOrder.verify(unavailableTimeRepository)
            .save(any(UnavailableTime.class));
    }

    @Test
    @DisplayName("UNA-01 - 시작 시간과 종료 시간이 같으면 등록할 수 없다")
    void registerThrowsExceptionWhenStartEqualsEnd() {

        // given
        LocalDateTime sameTime =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        mockRegisterUser();

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.register(
                    sameTime,
                    sameTime,
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.INVALID_TIME_RANGE
            );

        verify(unavailableTimeRepository, never())
            .save(any());
    }

    @Test
    @DisplayName("UNA-01 - 시작 시간이 종료 시간보다 늦으면 등록할 수 없다")
    void registerThrowsExceptionWhenStartIsAfterEnd() {

        // given
        LocalDateTime startAt =
            LocalDateTime.of(2026, 9, 22, 12, 0);

        LocalDateTime endAt =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        mockRegisterUser();

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.register(
                    startAt,
                    endAt,
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.INVALID_TIME_RANGE
            );

        verify(unavailableTimeRepository, never())
            .save(any());
    }

    @Test
    @DisplayName("UNA-01 - 시작 시간이 현재 시간과 같으면 등록할 수 없다")
    void registerThrowsExceptionWhenStartEqualsNow() {

        // given
        mockRegisterUser();

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.register(
                    NOW,
                    NOW.plusHours(1),
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.NOT_FUTURE_TIME
            );

        verify(unavailableTimeRepository, never())
            .save(any());
    }

    @Test
    @DisplayName("UNA-01 - 시작 시간이 현재 시간보다 이전이면 등록할 수 없다")
    void registerThrowsExceptionWhenStartIsPast() {

        // given
        mockRegisterUser();

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.register(
                    NOW.minusMinutes(1),
                    NOW.plusHours(1),
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.NOT_FUTURE_TIME
            );

        verify(unavailableTimeRepository, never())
            .save(any());
    }

    @Test
    @DisplayName("UNA-01 - 새 일정의 앞부분이 기존 일정과 겹치면 등록할 수 없다")
    void registerThrowsExceptionWhenFrontPartOverlaps() {

        assertRegisterOverlap(
            LocalDateTime.of(2026, 9, 22, 9, 0),
            LocalDateTime.of(2026, 9, 22, 11, 0)
        );
    }

    @Test
    @DisplayName("UNA-01 - 새 일정의 뒷부분이 기존 일정과 겹치면 등록할 수 없다")
    void registerThrowsExceptionWhenBackPartOverlaps() {

        assertRegisterOverlap(
            LocalDateTime.of(2026, 9, 22, 9, 0),
            LocalDateTime.of(2026, 9, 22, 11, 0)
        );
    }

    @Test
    @DisplayName("UNA-01 - 새 일정이 기존 일정 안에 포함되면 등록할 수 없다")
    void registerThrowsExceptionWhenNewTimeIsInsideExistingTime() {

        assertRegisterOverlap(
            LocalDateTime.of(2026, 9, 22, 9, 0),
            LocalDateTime.of(2026, 9, 22, 11, 0)
        );
    }

    @Test
    @DisplayName("UNA-01 - 새 일정이 기존 일정을 포함하면 등록할 수 없다")
    void registerThrowsExceptionWhenNewTimeContainsExistingTime() {

        assertRegisterOverlap(
            LocalDateTime.of(2026, 9, 22, 9, 0),
            LocalDateTime.of(2026, 9, 22, 11, 0)
        );
    }

    @Test
    @DisplayName("UNA-01 - 새 일정 종료와 기존 일정 시작이 같으면 등록할 수 있다")
    void registerSucceedsWhenNewEndEqualsExistingStart() {

        // given
        LocalDateTime newStartAt =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        LocalDateTime newEndAt =
            LocalDateTime.of(2026, 9, 22, 12, 0);

        assertRegisterAdjacentSuccess(
            newStartAt,
            newEndAt
        );
    }

    @Test
    @DisplayName("UNA-01 - 기존 일정 종료와 새 일정 시작이 같으면 등록할 수 있다")
    void registerSucceedsWhenNewStartEqualsExistingEnd() {

        // given
        LocalDateTime newStartAt =
            LocalDateTime.of(2026, 9, 22, 12, 0);

        LocalDateTime newEndAt =
            LocalDateTime.of(2026, 9, 22, 14, 0);

        assertRegisterAdjacentSuccess(
            newStartAt,
            newEndAt
        );
    }

    @Test
    @DisplayName("UNA-01 - 공식 Shift와 충돌하고 확인하지 않으면 등록할 수 없다")
    void registerThrowsExceptionWhenOfficialShiftConflictIsNotConfirmed() {

        // given
        LocalDateTime startAt =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        LocalDateTime endAt =
            LocalDateTime.of(2026, 9, 22, 12, 0);

        User user = mockRegisterUser();

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTime(
                    USER_ID,
                    startAt,
                    endAt
                )
        ).thenReturn(false);

        when(
            shiftRepository.existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                startAt,
                endAt,
                null
            )
        ).thenReturn(true);

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.register(
                    startAt,
                    endAt,
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT
            );

        verify(unavailableTimeRepository, never())
            .save(any());
    }

    @Test
    @DisplayName("UNA-01 - 공식 Shift와 충돌해도 확인하면 등록할 수 있다")
    void registerSucceedsWhenOfficialShiftConflictIsConfirmed() {

        // given
        LocalDateTime startAt =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        LocalDateTime endAt =
            LocalDateTime.of(2026, 9, 22, 12, 0);

        User user = mockRegisterUser();

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTime(
                    USER_ID,
                    startAt,
                    endAt
                )
        ).thenReturn(false);

        when(
            shiftRepository.existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                startAt,
                endAt,
                null
            )
        ).thenReturn(true);

        when(unavailableTimeRepository.save(any(UnavailableTime.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UnavailableTime result =
            unavailableTimeService.register(
                startAt,
                endAt,
                true
            );

        // then
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getStartAt()).isEqualTo(startAt);
        assertThat(result.getEndAt()).isEqualTo(endAt);

        verify(unavailableTimeRepository)
            .save(any(UnavailableTime.class));
    }

    // =========================================================
    // UNA-02 조회
    // =========================================================

    @Test
    @DisplayName("UNA-02 - 미래 근무 불가능 일정을 조회한다")
    void findAllReturnsFutureUnavailableTimes() {

        // given
        LocalDateTime startAt =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        LocalDateTime endAt =
            LocalDateTime.of(2026, 9, 22, 12, 0);

        UnavailableTime unavailableTime =
            org.mockito.Mockito.mock(UnavailableTime.class);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(
            unavailableTimeRepository
                .findByUserIdAndEndAtAfterOrderByStartAtAsc(
                    USER_ID,
                    NOW
                )
        ).thenReturn(List.of(unavailableTime));

        when(unavailableTime.getId())
            .thenReturn(10L);

        when(unavailableTime.getStartAt())
            .thenReturn(startAt);

        when(unavailableTime.getEndAt())
            .thenReturn(endAt);

        when(
            unavailableTimeRepository
                .findIdsWithOfficialShiftConflict(
                    USER_ID,
                    NOW
                )
        ).thenReturn(List.of());

        // when
        List<UnavailableTimeService.UnavailableTimeResult> result =
            unavailableTimeService.findAll();

        // then
        assertThat(result).hasSize(1);

        assertThat(result.get(0).unavailableTimeId())
            .isEqualTo(10L);

        assertThat(result.get(0).startAt())
            .isEqualTo(startAt);

        assertThat(result.get(0).endAt())
            .isEqualTo(endAt);

        assertThat(result.get(0).officialShiftConflict())
            .isFalse();

        verify(unavailableTimeRepository)
            .findIdsWithOfficialShiftConflict(
                USER_ID,
                NOW
            );

        verify(
            shiftRepository,
            never()
        ).existsOverlappingOfficialShift(
            any(),
            any(),
            any(),
            any(),
            any()
        );

    }

    @Test
    @DisplayName("UNA-02 - 공식 Shift와 겹치면 officialShiftConflict가 true다")
    void findAllReturnsOfficialShiftConflictTrue() {

        // given
        LocalDateTime startAt =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        LocalDateTime endAt =
            LocalDateTime.of(2026, 9, 22, 12, 0);

        UnavailableTime unavailableTime =
            org.mockito.Mockito.mock(UnavailableTime.class);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(
            unavailableTimeRepository
                .findByUserIdAndEndAtAfterOrderByStartAtAsc(
                    USER_ID,
                    NOW
                )
        ).thenReturn(List.of(unavailableTime));

        when(unavailableTime.getId())
            .thenReturn(10L);

        when(unavailableTime.getStartAt())
            .thenReturn(startAt);

        when(unavailableTime.getEndAt())
            .thenReturn(endAt);

        when(
            unavailableTimeRepository
                .findIdsWithOfficialShiftConflict(
                    USER_ID,
                    NOW
                )
        ).thenReturn(
            List.of(10L)
        );

        // when
        List<UnavailableTimeService.UnavailableTimeResult> result =
            unavailableTimeService.findAll();

        // then
        assertThat(result).hasSize(1);

        assertThat(result.get(0).officialShiftConflict())
            .isTrue();

        verify(unavailableTimeRepository)
            .findIdsWithOfficialShiftConflict(
                USER_ID,
                NOW
            );

    }

    @Test
    @DisplayName("UNA-02 - 조회할 일정이 없으면 빈 리스트를 반환한다")
    void findAllReturnsEmptyListWhenNoUnavailableTimeExists() {

        // given
        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(
            unavailableTimeRepository
                .findByUserIdAndEndAtAfterOrderByStartAtAsc(
                    USER_ID,
                    NOW
                )
        ).thenReturn(List.of());

        // when
        List<UnavailableTimeService.UnavailableTimeResult> result =
            unavailableTimeService.findAll();

        // then
        assertThat(result).isEmpty();

        verify(unavailableTimeRepository)
            .findByUserIdAndEndAtAfterOrderByStartAtAsc(
                USER_ID,
                NOW
            );

        verify(
            unavailableTimeRepository,
            never()
        ).findIdsWithOfficialShiftConflict(
            any(),
            any()
        );
    }

    // =========================================================
    // UNA-03 수정
    // =========================================================

    @Test
    @DisplayName("UNA-03 - 본인의 미래 일정을 정상적으로 수정한다")
    void updateSucceedsWithValidRequest() {

        // given
        Long unavailableTimeId = 10L;

        LocalDateTime oldStartAt =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        LocalDateTime newStartAt =
            LocalDateTime.of(2026, 9, 22, 13, 0);

        LocalDateTime newEndAt =
            LocalDateTime.of(2026, 9, 22, 15, 0);

        User user = mockUser(USER_ID);

        UnavailableTime unavailableTime =
            org.mockito.Mockito.mock(UnavailableTime.class);

        when(unavailableTime.getUser())
            .thenReturn(user);

        when(unavailableTime.getStartAt())
            .thenReturn(oldStartAt);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.of(unavailableTime));

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTimeExcludingId(
                    USER_ID,
                    unavailableTimeId,
                    newStartAt,
                    newEndAt
                )
        ).thenReturn(false);

        when(
            shiftRepository.existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                newStartAt,
                newEndAt,
                null
            )
        ).thenReturn(false);

        // when
        UnavailableTime result =
            unavailableTimeService.update(
                unavailableTimeId,
                newStartAt,
                newEndAt,
                false
            );

        // then
        assertThat(result).isEqualTo(unavailableTime);

        verify(unavailableTime)
            .update(
                newStartAt,
                newEndAt
            );
        InOrder callOrder =
            inOrder(
                userService,
                unavailableTimeRepository,
                shiftRepository,
                unavailableTime
            );

        callOrder.verify(userService)
            .getByIdForUpdate(USER_ID);

        callOrder.verify(unavailableTimeRepository)
            .findById(unavailableTimeId);

        callOrder.verify(unavailableTimeRepository)
            .existsOverlappingUnavailableTimeExcludingId(
                USER_ID,
                unavailableTimeId,
                newStartAt,
                newEndAt
            );

        callOrder.verify(shiftRepository)
            .existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                newStartAt,
                newEndAt,
                null
            );

        callOrder.verify(unavailableTime)
            .update(
                newStartAt,
                newEndAt
            );
    }

    @Test
    @DisplayName("UNA-03 - 존재하지 않는 일정은 수정할 수 없다")
    void updateThrowsExceptionWhenUnavailableTimeDoesNotExist() {

        // given
        Long unavailableTimeId = 999L;

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.empty());

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.update(
                    unavailableTimeId,
                    NOW.plusHours(1),
                    NOW.plusHours(2),
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.NOT_FOUND
            );
    }

    @Test
    @DisplayName("UNA-03 - 다른 사용자의 일정은 수정할 수 없다")
    void updateThrowsExceptionWhenUserIsNotOwner() {

        // given
        Long unavailableTimeId = 10L;

        User otherUser = mockUser(999L);

        UnavailableTime unavailableTime =
            org.mockito.Mockito.mock(UnavailableTime.class);

        when(unavailableTime.getUser())
            .thenReturn(otherUser);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.of(unavailableTime));

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.update(
                    unavailableTimeId,
                    NOW.plusHours(1),
                    NOW.plusHours(2),
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.FORBIDDEN
            );

        verify(unavailableTime, never())
            .update(any(), any());
    }

    @Test
    @DisplayName("UNA-03 - 기존 일정 시작 시각이 현재 시각과 같으면 수정할 수 없다")
    void updateThrowsExceptionWhenExistingStartEqualsNow() {

        // given
        Long unavailableTimeId = 10L;

        User user = mockUser(USER_ID);

        UnavailableTime unavailableTime =
            org.mockito.Mockito.mock(UnavailableTime.class);

        when(unavailableTime.getUser())
            .thenReturn(user);

        when(unavailableTime.getStartAt())
            .thenReturn(NOW);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.of(unavailableTime));

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.update(
                    unavailableTimeId,
                    NOW.plusHours(1),
                    NOW.plusHours(2),
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.NOT_MODIFIABLE_TIME
            );

        verify(unavailableTime, never())
            .update(any(), any());
    }

    @Test
    @DisplayName("UNA-03 - 기존 일정이 이미 시작되었으면 수정할 수 없다")
    void updateThrowsExceptionWhenExistingStartIsPast() {

        // given
        Long unavailableTimeId = 10L;

        User user = mockUser(USER_ID);

        UnavailableTime unavailableTime =
            org.mockito.Mockito.mock(UnavailableTime.class);

        when(unavailableTime.getUser())
            .thenReturn(user);

        when(unavailableTime.getStartAt())
            .thenReturn(NOW.minusMinutes(1));

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.of(unavailableTime));

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.update(
                    unavailableTimeId,
                    NOW.plusHours(1),
                    NOW.plusHours(2),
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.NOT_MODIFIABLE_TIME
            );

        verify(unavailableTime, never())
            .update(any(), any());
    }

    @Test
    @DisplayName("UNA-03 - 새 시작 시간과 종료 시간이 같으면 수정할 수 없다")
    void updateThrowsExceptionWhenNewStartEqualsEnd() {

        // given
        Long unavailableTimeId = 10L;

        LocalDateTime sameTime =
            NOW.plusHours(2);

        UnavailableTime unavailableTime =
            mockOwnedFutureUnavailableTime(
                unavailableTimeId,
                NOW.plusDays(1)
            );

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.update(
                    unavailableTimeId,
                    sameTime,
                    sameTime,
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.INVALID_TIME_RANGE
            );

        verify(unavailableTime, never())
            .update(any(), any());
    }

    @Test
    @DisplayName("UNA-03 - 새 시작 시간이 종료 시간보다 늦으면 수정할 수 없다")
    void updateThrowsExceptionWhenNewStartIsAfterEnd() {

        // given
        Long unavailableTimeId = 10L;

        UnavailableTime unavailableTime =
            mockOwnedFutureUnavailableTime(
                unavailableTimeId,
                NOW.plusDays(1)
            );

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.update(
                    unavailableTimeId,
                    NOW.plusHours(3),
                    NOW.plusHours(2),
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.INVALID_TIME_RANGE
            );

        verify(unavailableTime, never())
            .update(any(), any());
    }

    @Test
    @DisplayName("UNA-03 - 새 시작 시간이 현재 시간과 같으면 수정할 수 없다")
    void updateThrowsExceptionWhenNewStartEqualsNow() {

        // given
        Long unavailableTimeId = 10L;

        UnavailableTime unavailableTime =
            mockOwnedFutureUnavailableTime(
                unavailableTimeId,
                NOW.plusDays(1)
            );

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.update(
                    unavailableTimeId,
                    NOW,
                    NOW.plusHours(1),
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.NOT_FUTURE_TIME
            );

        verify(unavailableTime, never())
            .update(any(), any());
    }

    @Test
    @DisplayName("UNA-03 - 새 시작 시간이 과거이면 수정할 수 없다")
    void updateThrowsExceptionWhenNewStartIsPast() {

        // given
        Long unavailableTimeId = 10L;

        UnavailableTime unavailableTime =
            mockOwnedFutureUnavailableTime(
                unavailableTimeId,
                NOW.plusDays(1)
            );

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.update(
                    unavailableTimeId,
                    NOW.minusMinutes(1),
                    NOW.plusHours(1),
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.NOT_FUTURE_TIME
            );

        verify(unavailableTime, never())
            .update(any(), any());
    }

    @Test
    @DisplayName("UNA-03 - 수정 시 자기 자신은 중복 검사에서 제외한다")
    void updateExcludesItselfFromOverlapValidation() {

        // given
        Long unavailableTimeId = 10L;

        LocalDateTime newStartAt =
            LocalDateTime.of(2026, 9, 22, 11, 0);

        LocalDateTime newEndAt =
            LocalDateTime.of(2026, 9, 22, 13, 0);

        UnavailableTime unavailableTime =
            mockOwnedFutureUnavailableTime(
                unavailableTimeId,
                LocalDateTime.of(
                    2026, 9, 22, 10, 0
                )
            );

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTimeExcludingId(
                    USER_ID,
                    unavailableTimeId,
                    newStartAt,
                    newEndAt
                )
        ).thenReturn(false);

        when(
            shiftRepository.existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                newStartAt,
                newEndAt,
                null
            )
        ).thenReturn(false);

        // when
        UnavailableTime result =
            unavailableTimeService.update(
                unavailableTimeId,
                newStartAt,
                newEndAt,
                false
            );

        // then
        assertThat(result).isEqualTo(unavailableTime);

        verify(unavailableTimeRepository)
            .existsOverlappingUnavailableTimeExcludingId(
                USER_ID,
                unavailableTimeId,
                newStartAt,
                newEndAt
            );

        verify(unavailableTime)
            .update(
                newStartAt,
                newEndAt
            );
    }

    @Test
    @DisplayName("UNA-03 - 다른 근무 불가능 일정과 겹치면 수정할 수 없다")
    void updateThrowsExceptionWhenAnotherUnavailableTimeOverlaps() {

        // given
        Long unavailableTimeId = 10L;

        LocalDateTime newStartAt =
            LocalDateTime.of(2026, 9, 22, 11, 0);

        LocalDateTime newEndAt =
            LocalDateTime.of(2026, 9, 22, 13, 0);

        UnavailableTime target =
            mockOwnedFutureUnavailableTime(
                unavailableTimeId,
                LocalDateTime.of(
                    2026, 9, 23, 10, 0
                )
            );

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTimeExcludingId(
                    USER_ID,
                    unavailableTimeId,
                    newStartAt,
                    newEndAt
                )
        ).thenReturn(true);

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.update(
                    unavailableTimeId,
                    newStartAt,
                    newEndAt,
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.TIME_OVERLAP
            );

        verify(unavailableTimeRepository)
            .existsOverlappingUnavailableTimeExcludingId(
                USER_ID,
                unavailableTimeId,
                newStartAt,
                newEndAt
            );

        verify(target, never())
            .update(any(), any());
    }

    @Test
    @DisplayName("UNA-03 - 공식 Shift와 충돌하고 확인하지 않으면 수정할 수 없다")
    void updateThrowsExceptionWhenOfficialShiftConflictIsNotConfirmed() {

        // given
        Long unavailableTimeId = 10L;

        LocalDateTime newStartAt =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        LocalDateTime newEndAt =
            LocalDateTime.of(2026, 9, 22, 12, 0);

        UnavailableTime unavailableTime =
            mockOwnedFutureUnavailableTime(
                unavailableTimeId,
                LocalDateTime.of(
                    2026, 9, 23, 10, 0
                )
            );

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTimeExcludingId(
                    USER_ID,
                    unavailableTimeId,
                    newStartAt,
                    newEndAt
                )
        ).thenReturn(false);

        when(
            shiftRepository.existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                newStartAt,
                newEndAt,
                null
            )
        ).thenReturn(true);

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.update(
                    unavailableTimeId,
                    newStartAt,
                    newEndAt,
                    false
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT
            );

        verify(unavailableTimeRepository)
            .existsOverlappingUnavailableTimeExcludingId(
                USER_ID,
                unavailableTimeId,
                newStartAt,
                newEndAt
            );

        verify(unavailableTime, never())
            .update(any(), any());
    }

    @Test
    @DisplayName("UNA-03 - 공식 Shift와 충돌해도 확인하면 수정할 수 있다")
    void updateSucceedsWhenOfficialShiftConflictIsConfirmed() {

        // given
        Long unavailableTimeId = 10L;

        LocalDateTime newStartAt =
            LocalDateTime.of(2026, 9, 22, 10, 0);

        LocalDateTime newEndAt =
            LocalDateTime.of(2026, 9, 22, 12, 0);

        UnavailableTime unavailableTime =
            mockOwnedFutureUnavailableTime(
                unavailableTimeId,
                LocalDateTime.of(
                    2026, 9, 23, 10, 0
                )
            );

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTimeExcludingId(
                    USER_ID,
                    unavailableTimeId,
                    newStartAt,
                    newEndAt
                )
        ).thenReturn(false);

        when(
            shiftRepository.existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                newStartAt,
                newEndAt,
                null
            )
        ).thenReturn(true);

        // when
        UnavailableTime result =
            unavailableTimeService.update(
                unavailableTimeId,
                newStartAt,
                newEndAt,
                true
            );

        // then
        assertThat(result).isEqualTo(unavailableTime);

        verify(unavailableTime)
            .update(
                newStartAt,
                newEndAt
            );
    }

    // =========================================================
    // UNA-04 삭제
    // =========================================================

    @Test
    @DisplayName("UNA-04 - 본인의 근무 불가능 일정을 삭제한다")
    void deleteSucceedsWhenUserIsOwner() {

        // given
        Long unavailableTimeId = 10L;

        User user = mockUser(USER_ID);

        UnavailableTime unavailableTime =
            org.mockito.Mockito.mock(UnavailableTime.class);

        when(unavailableTime.getUser())
            .thenReturn(user);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.of(unavailableTime));

        // when
        unavailableTimeService.delete(unavailableTimeId);

        // then
        verify(unavailableTimeRepository)
            .delete(unavailableTime);
    }

    @Test
    @DisplayName("UNA-04 - 존재하지 않는 일정은 삭제할 수 없다")
    void deleteThrowsExceptionWhenUnavailableTimeDoesNotExist() {

        // given
        Long unavailableTimeId = 999L;

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.empty());

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.delete(
                    unavailableTimeId
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.NOT_FOUND
            );

        verify(unavailableTimeRepository, never())
            .delete(any());
    }

    @Test
    @DisplayName("UNA-04 - 다른 사용자의 일정은 삭제할 수 없다")
    void deleteThrowsExceptionWhenUserIsNotOwner() {

        // given
        Long unavailableTimeId = 10L;

        User otherUser = mockUser(999L);

        UnavailableTime unavailableTime =
            org.mockito.Mockito.mock(UnavailableTime.class);

        when(unavailableTime.getUser())
            .thenReturn(otherUser);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.of(unavailableTime));

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.delete(
                    unavailableTimeId
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.FORBIDDEN
            );

        verify(unavailableTimeRepository, never())
            .delete(any());
    }

    @Test
    @DisplayName("UNA-04 - 삭제 시 시간 검증 없이 본인 일정은 삭제할 수 있다")
    void deleteDoesNotValidateTime() {

        // given
        Long unavailableTimeId = 10L;

        User user = mockUser(USER_ID);

        UnavailableTime unavailableTime =
            org.mockito.Mockito.mock(UnavailableTime.class);

        when(unavailableTime.getUser())
            .thenReturn(user);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.of(unavailableTime));

        // when
        unavailableTimeService.delete(unavailableTimeId);

        // then
        verify(unavailableTimeRepository)
            .delete(unavailableTime);

        verify(unavailableTime, never())
            .getStartAt();

        verify(unavailableTime, never())
            .getEndAt();

        verify(shiftRepository, never())
            .existsOverlappingOfficialShift(
                any(),
                any(),
                any(),
                any(),
                any()
            );
    }

    // =========================================================
    // 테스트 Helper
    // =========================================================

    private User mockRegisterUser() {

        User user =
            org.mockito.Mockito.mock(User.class);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(userService.getByIdForUpdate(USER_ID))
            .thenReturn(user);

        return user;
    }

    private User mockUser(Long userId) {

        User user =
            org.mockito.Mockito.mock(User.class);

        when(user.getId())
            .thenReturn(userId);

        return user;
    }

    private UnavailableTime mockOwnedFutureUnavailableTime(
        Long unavailableTimeId,
        LocalDateTime existingStartAt
    ) {

        User user = mockUser(USER_ID);

        UnavailableTime unavailableTime =
            org.mockito.Mockito.mock(UnavailableTime.class);

        lenient()
            .when(unavailableTime.getId())
            .thenReturn(unavailableTimeId);

        when(unavailableTime.getUser())
            .thenReturn(user);

        when(unavailableTime.getStartAt())
            .thenReturn(existingStartAt);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.of(unavailableTime));

        return unavailableTime;
    }

    private void assertRegisterOverlap(
        LocalDateTime newStartAt,
        LocalDateTime newEndAt
    ) {

        mockRegisterUser();

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTime(
                    USER_ID,
                    newStartAt,
                    newEndAt
                )
        ).thenReturn(true);

        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> unavailableTimeService.register(
                    newStartAt,
                    newEndAt,
                    false
                )
            );

        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.TIME_OVERLAP
            );

        verify(
            unavailableTimeRepository
        ).existsOverlappingUnavailableTime(
            USER_ID,
            newStartAt,
            newEndAt
        );

        verify(
            unavailableTimeRepository,
            never()
        ).save(any());
    }

    private void assertRegisterAdjacentSuccess(
        LocalDateTime newStartAt,
        LocalDateTime newEndAt
    ) {

        User user = mockRegisterUser();

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTime(
                    USER_ID,
                    newStartAt,
                    newEndAt
                )
        ).thenReturn(false);

        when(
            shiftRepository.existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                newStartAt,
                newEndAt,
                null
            )
        ).thenReturn(false);

        when(
            unavailableTimeRepository.save(
                any(UnavailableTime.class)
            )
        ).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        UnavailableTime result =
            unavailableTimeService.register(
                newStartAt,
                newEndAt,
                false
            );

        assertThat(result.getStartAt())
            .isEqualTo(newStartAt);

        assertThat(result.getEndAt())
            .isEqualTo(newEndAt);

        verify(
            unavailableTimeRepository
        ).existsOverlappingUnavailableTime(
            USER_ID,
            newStartAt,
            newEndAt
        );

        verify(unavailableTimeRepository)
            .save(any(UnavailableTime.class));
    }

    @Test
    @DisplayName("UNA-01: 공식 Shift와 충돌하고 확인하지 않으면 Warning 예외가 발생한다")
    void register_officialShiftConflict_withoutConfirmation_throwsWarning() {
        // given
        LocalDateTime startAt = NOW.plusDays(1).withHour(10);
        LocalDateTime endAt = NOW.plusDays(1).withHour(12);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(userService.getByIdForUpdate(USER_ID))
            .thenReturn(user);

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTime(
                    USER_ID,
                    startAt,
                    endAt
                )
        ).thenReturn(false);

        when(shiftRepository.existsOverlappingOfficialShift(
            USER_ID,
            ScheduleStatus.PUBLISHED,
            startAt,
            endAt,
            null
        )).thenReturn(true);

        // when
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> unavailableTimeService.register(
                startAt,
                endAt,
                false
            )
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT
            );

        assertThat(exception.getData())
            .isInstanceOf(ConfirmationRequiredResponse.class);

        ConfirmationRequiredResponse data =
            (ConfirmationRequiredResponse) exception.getData();

        assertThat(data.requiresConfirmation())
            .isTrue();

        verify(unavailableTimeRepository, never())
            .save(any(UnavailableTime.class));
    }

    @Test
    @DisplayName("UNA-01: 공식 Shift와 충돌하고 확인값이 null이면 Warning 예외가 발생한다")
    void register_officialShiftConflict_withNullConfirmation_throwsWarning() {
        // given
        LocalDateTime startAt = NOW.plusDays(1).withHour(10);
        LocalDateTime endAt = NOW.plusDays(1).withHour(12);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(userService.getByIdForUpdate(USER_ID))
            .thenReturn(user);

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTime(
                    USER_ID,
                    startAt,
                    endAt
                )
        ).thenReturn(false);

        when(shiftRepository.existsOverlappingOfficialShift(
            USER_ID,
            ScheduleStatus.PUBLISHED,
            startAt,
            endAt,
            null
        )).thenReturn(true);

        // when
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> unavailableTimeService.register(
                startAt,
                endAt,
                null
            )
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT
            );

        assertThat(exception.getData())
            .isInstanceOf(ConfirmationRequiredResponse.class);

        ConfirmationRequiredResponse data =
            (ConfirmationRequiredResponse) exception.getData();

        assertThat(data.requiresConfirmation())
            .isTrue();

        verify(shiftRepository)
            .existsOverlappingOfficialShift(
                USER_ID,
                ScheduleStatus.PUBLISHED,
                startAt,
                endAt,
                null
            );

        verify(unavailableTimeRepository, never())
            .save(any(UnavailableTime.class));
    }

    @Test
    @DisplayName("UNA-01: 공식 Shift와 충돌해도 사용자가 확인하면 등록할 수 있다")
    void register_officialShiftConflict_withConfirmation_success() {
        // given
        LocalDateTime startAt = NOW.plusDays(1).withHour(10);
        LocalDateTime endAt = NOW.plusDays(1).withHour(12);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(userService.getByIdForUpdate(USER_ID))
            .thenReturn(user);

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTime(
                    USER_ID,
                    startAt,
                    endAt
                )
        ).thenReturn(false);

        when(shiftRepository.existsOverlappingOfficialShift(
            USER_ID,
            ScheduleStatus.PUBLISHED,
            startAt,
            endAt,
            null
        )).thenReturn(true);

        when(unavailableTimeRepository.save(any(UnavailableTime.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UnavailableTime result =
            unavailableTimeService.register(
                startAt,
                endAt,
                true
            );

        // then
        assertThat(result.getStartAt())
            .isEqualTo(startAt);

        assertThat(result.getEndAt())
            .isEqualTo(endAt);

        verify(unavailableTimeRepository)
            .save(any(UnavailableTime.class));
    }

    @Test
    @DisplayName("UNA-03: 수정 시간이 공식 Shift와 충돌하고 확인하지 않으면 Warning 예외가 발생한다")
    void update_officialShiftConflict_withoutConfirmation_throwsWarning() {
        // given
        Long unavailableTimeId = 1L;

        LocalDateTime existingStartAt =
            NOW.plusDays(1).withHour(9);

        LocalDateTime existingEndAt =
            NOW.plusDays(1).withHour(11);

        LocalDateTime newStartAt =
            NOW.plusDays(2).withHour(10);

        LocalDateTime newEndAt =
            NOW.plusDays(2).withHour(12);

        UnavailableTime unavailableTime =
            new UnavailableTime(
                user,
                existingStartAt,
                existingEndAt
            );

        when(user.getId()).thenReturn(USER_ID);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.of(unavailableTime));

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTimeExcludingId(
                    USER_ID,
                    unavailableTimeId,
                    newStartAt,
                    newEndAt
                )
        ).thenReturn(false);

        when(shiftRepository.existsOverlappingOfficialShift(
            USER_ID,
            ScheduleStatus.PUBLISHED,
            newStartAt,
            newEndAt,
            null
        )).thenReturn(true);

        // when
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> unavailableTimeService.update(
                unavailableTimeId,
                newStartAt,
                newEndAt,
                false
            )
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT
            );

        assertThat(exception.getData())
            .isInstanceOf(ConfirmationRequiredResponse.class);

        ConfirmationRequiredResponse data =
            (ConfirmationRequiredResponse) exception.getData();

        assertThat(data.requiresConfirmation())
            .isTrue();

        // Warning 때문에 수정되지 않았는지도 확인
        assertThat(unavailableTime.getStartAt())
            .isEqualTo(existingStartAt);

        assertThat(unavailableTime.getEndAt())
            .isEqualTo(existingEndAt);
    }

    @Test
    @DisplayName("UNA-03: 수정 시간이 공식 Shift와 충돌해도 사용자가 확인하면 수정할 수 있다")
    void update_officialShiftConflict_withConfirmation_success() {
        // given
        Long unavailableTimeId = 1L;

        LocalDateTime existingStartAt =
            NOW.plusDays(1).withHour(9);

        LocalDateTime existingEndAt =
            NOW.plusDays(1).withHour(11);

        LocalDateTime newStartAt =
            NOW.plusDays(2).withHour(10);

        LocalDateTime newEndAt =
            NOW.plusDays(2).withHour(12);

        UnavailableTime unavailableTime =
            new UnavailableTime(
                user,
                existingStartAt,
                existingEndAt
            );

        when(user.getId()).thenReturn(USER_ID);

        when(rq.getActorId())
            .thenReturn(USER_ID);

        when(unavailableTimeRepository.findById(unavailableTimeId))
            .thenReturn(Optional.of(unavailableTime));

        when(
            unavailableTimeRepository
                .existsOverlappingUnavailableTimeExcludingId(
                    USER_ID,
                    unavailableTimeId,
                    newStartAt,
                    newEndAt
                )
        ).thenReturn(false);

        when(shiftRepository.existsOverlappingOfficialShift(
            USER_ID,
            ScheduleStatus.PUBLISHED,
            newStartAt,
            newEndAt,
            null
        )).thenReturn(true);

        // when
        UnavailableTime result =
            unavailableTimeService.update(
                unavailableTimeId,
                newStartAt,
                newEndAt,
                true
            );

        // then
        assertThat(result.getStartAt())
            .isEqualTo(newStartAt);

        assertThat(result.getEndAt())
            .isEqualTo(newEndAt);
    }

}
