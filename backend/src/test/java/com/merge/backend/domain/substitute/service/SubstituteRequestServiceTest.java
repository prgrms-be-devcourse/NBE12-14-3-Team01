package com.merge.backend.domain.substitute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.merge.backend.domain.shift.entity.Schedule;
import com.merge.backend.domain.shift.entity.ScheduleStatus;
import com.merge.backend.domain.shift.entity.Shift;
import com.merge.backend.domain.shift.entity.ShiftStatus;
import com.merge.backend.domain.shift.exception.ScheduleErrorCode;
import com.merge.backend.domain.shift.exception.ShiftErrorCode;
import com.merge.backend.domain.shift.repository.ShiftRepository;
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository;
import com.merge.backend.domain.substitute.dto.SentSubstituteRequestResponse;
import com.merge.backend.domain.substitute.dto.SubstituteRequestCreateResponse;
import com.merge.backend.domain.substitute.dto.SubstituteRequestListResponse;
import com.merge.backend.domain.substitute.entity.CandidateStatus;
import com.merge.backend.domain.substitute.entity.RequestCloseReason;
import com.merge.backend.domain.substitute.entity.RequestStatus;
import com.merge.backend.domain.substitute.entity.SubstituteCandidate;
import com.merge.backend.domain.substitute.entity.SubstituteRequest;
import com.merge.backend.domain.substitute.event.SubstituteNoCandidateEvent;
import com.merge.backend.domain.substitute.event.SubstituteRequestApprovedEvent;
import com.merge.backend.domain.substitute.event.SubstituteRequestCreatedEvent;
import com.merge.backend.domain.substitute.event.SubstituteRequestManagerClosedEvent;
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode;
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository;
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.user.service.UserService;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.domain.workplace.repository.WorkplaceRepository;
import com.merge.backend.domain.workplace.service.WorkplaceMemberService;
import com.merge.backend.global.exception.BusinessException;
import com.merge.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubstituteRequestServiceTest {

    private static final Long SHIFT_ID = 1L;
    private static final Long REQUESTER_USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final Long SAVED_REQUEST_ID = 50L;
    private static final List<RequestStatus> ACTIVE_STATUSES =
        List.of(RequestStatus.OPEN, RequestStatus.ACCEPTED);

    private SubstituteRequestService substituteRequestService;

    @Mock
    private SubstituteRequestRepository substituteRequestRepository;

    @Mock
    private SubstituteCandidateRepository substituteCandidateRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private SubstituteCandidateService substituteCandidateService;

    @Mock
    private WorkplaceRepository workplaceRepository;

    @Mock
    private UnavailableTimeRepository unavailableTimeRepository;

    @Mock
    private WorkplaceMemberService workplaceMemberService;

    @Mock
    private WorkplaceMemberRepository workplaceMemberRepository;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private final Clock clock = Clock.fixed(
        Instant.parse("2026-09-22T12:00:00Z"),
        ZoneId.of("Asia/Seoul")
    );

    private Long requestId;
    private Long actorId;
    private Long workplaceId;
    private Long requesterMemberId;

    private Long candidateUserId;
    private WorkplaceMember member;
    private Workplace workplace;
    private WorkplaceMember requesterMember;
    private WorkplaceMember candidateMember;
    private User candidateUser;
    private Schedule schedule;
    private Shift shift;
    private SubstituteRequest request;
    private SubstituteCandidate candidate;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        substituteRequestService =
            new SubstituteRequestService(
                substituteRequestRepository,
                substituteCandidateService,
                substituteCandidateRepository,
                shiftRepository,
                workplaceRepository,
                unavailableTimeRepository,
                userService,
                workplaceMemberService,
                workplaceMemberRepository,
                clock,
                applicationEventPublisher
            );

        requestId = 1L;
        actorId = 10L;
        workplaceId = 100L;
        requesterMemberId = 15L;
        candidateUserId = 200L;
        now = LocalDateTime.now(clock);

        workplace = mock(Workplace.class);
        member = mock(WorkplaceMember.class);
        requesterMember = mock(WorkplaceMember.class);
        candidateMember = mock(WorkplaceMember.class);
        candidateUser = mock(User.class);
        schedule = mock(Schedule.class);
        shift = mock(Shift.class);
        request = mock(SubstituteRequest.class);
        candidate = mock(SubstituteCandidate.class);
    }

    private void setupValidRequestBasicInfo() {
        given(workplace.getId()).willReturn(workplaceId);
        given(schedule.getWorkplace()).willReturn(workplace);
        given(shift.getSchedule()).willReturn(schedule);
        given(request.getShift()).willReturn(shift);
        given(request.getStatus()).willReturn(RequestStatus.ACCEPTED);
    }

    private void setupValidRequestFullInfo() {
        setupValidRequestBasicInfo();
        given(schedule.getStatus()).willReturn(ScheduleStatus.PUBLISHED);
        given(shift.getStatus()).willReturn(ShiftStatus.SCHEDULED);
        given(requesterMember.getId()).willReturn(requesterMemberId);
        given(shift.getMember()).willReturn(requesterMember);
        given(request.getRequesterMember()).willReturn(requesterMember);
    }

    private void setupValidCandidateInfo() {
        given(candidateMember.getWorkplace()).willReturn(workplace);
        given(candidateMember.getLeftAt()).willReturn(null);
        given(candidateMember.getRole()).willReturn(WorkplaceRole.EMPLOYEE);
        given(candidateUser.getId()).willReturn(candidateUserId);
        given(candidateMember.getUser()).willReturn(candidateUser);
        given(candidate.getMember()).willReturn(candidateMember);
    }

    // 승인 직전 단계(Request/Workplace/ACCEPTED Candidate 조회)까지 정상 설정
    private void setupApprovableRequest() {
        setupValidRequestFullInfo();
        given(substituteRequestRepository.findByIdForUpdate(requestId))
            .willReturn(Optional.of(request));
        given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace));
        given(substituteCandidateRepository.findByRequestIdAndStatus(
            requestId, CandidateStatus.ACCEPTED))
            .willReturn(Optional.of(candidate));
    }

    // 예외 타입뿐 아니라 "어떤 에러코드"로 실패했는지까지 확인
    private static void assertBusinessError(ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(expected);
    }

    // 실패한 경우 승인(Shift 담당자 변경)과 이벤트 발행이 일어나지 않아야 한다
    private void assertApproveFails(ErrorCode expected) {
        assertBusinessError(() -> substituteRequestService.approve(requestId, actorId), expected);

        verify(request, never()).approveRequest(any(), any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    // 실패한 경우 종료 처리와 이벤트 발행이 일어나지 않아야 한다
    private void assertCloseFails(ErrorCode expected) {
        assertBusinessError(() -> substituteRequestService.close(requestId, actorId), expected);

        verify(request, never()).closeByManager(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    // ===================== SUB-04 대체 근무 승인 =====================

    @Nested
    @DisplayName("대체 근무 승인 (approve)")
    class ApproveTest {

        @Test
        @DisplayName("대체 근무 승인 성공")
        void approve_success() {

            // given
            setupValidRequestFullInfo();
            setupValidCandidateInfo();

            Long acceptedMemberId = 25L;

            given(request.getId())
                .willReturn(requestId);

            given(candidateMember.getId())
                .willReturn(acceptedMemberId);

            given(shift.getStartAt())
                .willReturn(
                    now.plusDays(1)
                );

            given(shift.getEndAt())
                .willReturn(
                    now.plusDays(1)
                        .plusHours(8)
                );

            given(
                substituteRequestRepository
                    .findByIdForUpdate(requestId)
            ).willReturn(
                Optional.of(request)
            );

            given(
                workplaceRepository
                    .findById(workplaceId)
            ).willReturn(
                Optional.of(workplace)
            );

            given(
                substituteCandidateRepository
                    .findByRequestIdAndStatus(
                        requestId,
                        CandidateStatus.ACCEPTED
                    )
            ).willReturn(
                Optional.of(candidate)
            );

            given(
                shiftRepository
                    .existsConflictingShift(
                        eq(candidateUserId),
                        any(),
                        any()
                    )
            ).willReturn(false);

            given(
                unavailableTimeRepository
                    .existsOverlappingUnavailableTime(
                        eq(candidateUserId),
                        any(),
                        any()
                    )
            ).willReturn(false);

            given(
                substituteCandidateRepository
                    .existsConflictingActiveSubstitute(
                        eq(candidateUserId),
                        eq(requestId),
                        any(),
                        any(),
                        any()
                    )
            ).willReturn(false);

            // when
            SubstituteRequest result =
                substituteRequestService.approve(
                    requestId,
                    actorId
                );

            // then
            assertThat(result)
                .isNotNull();

            InOrder lockOrder =
                inOrder(
                    substituteRequestRepository,
                    userService
                );

            lockOrder
                .verify(substituteRequestRepository)
                .findByIdForUpdate(requestId);

            lockOrder
                .verify(userService)
                .getByIdForUpdate(
                    candidateUserId
                );

            verify(workplaceMemberService)
                .requireManager(
                    actorId,
                    workplaceId
                );

            verify(request)
                .approveRequest(
                    eq(candidateMember),
                    any(LocalDateTime.class)
                );

            // 승인 Event 검증
            ArgumentCaptor<SubstituteRequestApprovedEvent>
                eventCaptor =
                ArgumentCaptor.forClass(
                    SubstituteRequestApprovedEvent.class
                );

            verify(applicationEventPublisher)
                .publishEvent(
                    eventCaptor.capture()
                );

            SubstituteRequestApprovedEvent event =
                eventCaptor.getValue();

            assertThat(event.requestId())
                .isEqualTo(requestId);

            assertThat(event.requesterMemberId())
                .isEqualTo(requesterMemberId);

            assertThat(event.acceptedMemberId())
                .isEqualTo(acceptedMemberId);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 요청이면 예외가 발생한다")
        void approve_fail_request_not_found() {
            // given
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.empty());

            // when & then
            assertBusinessError(
                () -> substituteRequestService.approve(requestId, actorId),
                SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND
            );
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @ParameterizedTest(name = "Request 상태 = {0}")
        @EnumSource(value = RequestStatus.class, names = {"OPEN", "APPROVED", "CLOSED"})
        @DisplayName("실패: 아무도 수락하지 않았거나(OPEN) 이미 끝난 요청은 승인할 수 없다")
        void approve_fail_invalid_request_status(RequestStatus status) {
            // given
            given(request.getStatus()).willReturn(status);
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.INVALID_REQUEST);

            // 상태 검증에서 막혔으므로 다음 단계(권한 확인, User Lock)로 넘어가지 않는다
            verify(workplaceMemberService, never()).requireManager(any(), any());
            verify(userService, never()).getByIdForUpdate(any());
        }

        @Test
        @DisplayName("실패: 승인 요청자가 해당 근무지 소속이 아니면 예외가 발생한다")
        void approve_fail_actor_not_in_workplace() {
            // given
            setupValidRequestBasicInfo();
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willThrow(new BusinessException(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER));

            // when & then
            assertApproveFails(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER);
            verify(substituteCandidateRepository, never()).findByRequestIdAndStatus(any(), any());
        }

        @Test
        @DisplayName("실패: 승인 요청자가 MANAGER가 아니면 예외가 발생한다")
        void approve_fail_actor_not_manager() {
            // given
            setupValidRequestBasicInfo();
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willThrow(new BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED));

            // when & then
            assertApproveFails(WorkplaceErrorCode.MANAGER_REQUIRED);
            verify(substituteCandidateRepository, never()).findByRequestIdAndStatus(any(), any());
        }

        @Test
        @DisplayName("실패: 근무표가 공개 전(DRAFT)이면 승인할 수 없다")
        void approve_fail_schedule_not_published() {
            // given
            setupValidRequestBasicInfo();
            given(schedule.getStatus()).willReturn(ScheduleStatus.DRAFT);
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));

            // when & then
            assertApproveFails(ScheduleErrorCode.NOT_PUBLISHED);
        }

        @Test
        @DisplayName("실패: Shift가 취소됐으면 승인할 수 없다")
        void approve_fail_shift_cancelled() {
            // given
            setupValidRequestBasicInfo();
            given(schedule.getStatus()).willReturn(ScheduleStatus.PUBLISHED);
            given(shift.getStatus()).willReturn(ShiftStatus.CANCELLED);
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));

            // when & then
            assertApproveFails(ShiftErrorCode.IS_CANCELED_SHIFT);
        }

        @Test
        @DisplayName("실패: 요청 이후 Shift 담당자가 바뀌었으면 승인할 수 없다")
        void approve_fail_shift_member_changed() {
            // given
            setupValidRequestBasicInfo();
            given(schedule.getStatus()).willReturn(ScheduleStatus.PUBLISHED);
            given(shift.getStatus()).willReturn(ShiftStatus.SCHEDULED);

            WorkplaceMember currentShiftMember = mock(WorkplaceMember.class);
            given(currentShiftMember.getId()).willReturn(999L);
            given(shift.getMember()).willReturn(currentShiftMember);

            given(requesterMember.getId()).willReturn(requesterMemberId);
            given(request.getRequesterMember()).willReturn(requesterMember);

            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.INVALID_MEMBER);
        }

        @Test
        @DisplayName("실패: Request는 ACCEPTED인데 ACCEPTED Candidate가 없으면 예외가 발생한다")
        void approve_fail_accepted_candidate_not_found() {
            // given
            setupValidRequestFullInfo();
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace));
            given(substituteCandidateRepository.findByRequestIdAndStatus(
                requestId, CandidateStatus.ACCEPTED))
                .willReturn(Optional.empty());

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.NOT_FOUND_CANDIDATE);
            verify(userService, never()).getByIdForUpdate(any());
        }

        @Test
        @DisplayName("실패: 수락한 사람이 퇴사했으면 승인으로 이어지지 않는다")
        void approve_fail_accepted_member_left() {
            // given
            setupApprovableRequest();
            given(candidate.getMember()).willReturn(candidateMember);
            given(candidateMember.getWorkplace()).willReturn(workplace);
            given(candidateMember.getLeftAt()).willReturn(now.minusHours(1));

            // when & then
            assertApproveFails(ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE);
            verify(userService, never()).getByIdForUpdate(any());
        }

        @Test
        @DisplayName("실패: 수락한 사람이 다른 근무지 소속이면 승인할 수 없다")
        void approve_fail_accepted_member_other_workplace() {
            // given
            setupApprovableRequest();
            Workplace otherWorkplace = mock(Workplace.class);
            given(otherWorkplace.getId()).willReturn(999L);
            given(candidate.getMember()).willReturn(candidateMember);
            given(candidateMember.getWorkplace()).willReturn(otherWorkplace);

            // when & then
            assertApproveFails(ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE);
            verify(userService, never()).getByIdForUpdate(any());
        }

        @Test
        @DisplayName("실패: 수락한 사람이 현재 EMPLOYEE가 아니면 승인할 수 없다")
        void approve_fail_candidate_not_employee() {
            // given
            setupApprovableRequest();
            given(candidate.getMember()).willReturn(candidateMember);
            given(candidateMember.getWorkplace()).willReturn(workplace);
            given(candidateMember.getLeftAt()).willReturn(null);

            // 수락 당시에는 EMPLOYEE였지만 승인 시점에는 MANAGER가 된 상황
            given(candidateMember.getRole()).willReturn(WorkplaceRole.MANAGER);

            // when & then
            assertApproveFails(ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE);

            // 승인 대상이 아니므로 User 시간 Lock까지 갈 필요가 없다
            verify(userService, never()).getByIdForUpdate(any());
        }

        @Test
        @DisplayName("실패: 근무 시작 시각이 이미 지났으면 승인할 수 없다")
        void approve_fail_past_shift_start_time() {
            // given
            setupApprovableRequest();
            setupValidCandidateInfo();
            given(shift.getStartAt()).willReturn(now.minusHours(1));

            // when & then
            assertApproveFails(ShiftErrorCode.INVALID_TIME_VALUE);
            verify(shiftRepository, never()).existsConflictingShift(any(), any(), any());
        }

        @Test
        @DisplayName("실패: 근무 시작 시각이 정확히 now(경계값)이면 승인할 수 없다")
        void approve_fail_shift_starts_exactly_now() {
            // given
            setupApprovableRequest();
            setupValidCandidateInfo();
            given(shift.getStartAt()).willReturn(now);

            // when & then
            assertApproveFails(ShiftErrorCode.INVALID_TIME_VALUE);
        }

        @Test
        @DisplayName("실패: 후보자에게 충돌하는 기존 Shift가 존재하면 승인할 수 없다")
        void approve_fail_conflicting_shift() {
            // given
            setupApprovableRequest();
            setupValidCandidateInfo();
            given(shift.getStartAt()).willReturn(now.plusDays(1));
            given(shift.getEndAt()).willReturn(now.plusDays(1).plusHours(8));

            given(shiftRepository.existsConflictingShift(
                candidateUserId, now.plusDays(1), now.plusDays(1).plusHours(8)))
                .willReturn(true);

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.CONFLICT_SHIFT);
        }

        @Test
        @DisplayName("실패: 후보자의 근무 불가 시간과 겹치면 승인할 수 없다")
        void approve_fail_conflicting_unavailable_time() {
            // given
            setupApprovableRequest();
            setupValidCandidateInfo();
            given(shift.getStartAt()).willReturn(now.plusDays(1));
            given(shift.getEndAt()).willReturn(now.plusDays(1).plusHours(8));

            given(unavailableTimeRepository.existsOverlappingUnavailableTime(
                candidateUserId, now.plusDays(1), now.plusDays(1).plusHours(8)))
                .willReturn(true);

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME);
        }

        @Test
        @DisplayName("실패: 후보자가 다른 활성 대타 약속과 겹치면 승인할 수 없다")
        void approve_fail_conflicting_active_substitute() {
            // given
            setupApprovableRequest();
            setupValidCandidateInfo();
            given(shift.getStartAt()).willReturn(now.plusDays(1));
            given(shift.getEndAt()).willReturn(now.plusDays(1).plusHours(8));

            given(substituteCandidateRepository.existsConflictingActiveSubstitute(
                candidateUserId, requestId,
                now.plusDays(1), now.plusDays(1).plusHours(8), now))
                .willReturn(true);

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE);
        }
    }

    // ===================== SUB-06/07 진행 중 요청 목록 조회 =====================

    @Nested
    @DisplayName("진행 중인 대타 요청 목록 조회 (list)")
    class ListTest {

        @Test
        @DisplayName("성공: OPEN과 ACCEPTED 요청이 섞여있으면, "
            + "ACCEPTED만 acceptedMember가 채워진 DTO 리스트를 반환한다.")
        void list_MixedStatusRequests_ReturnsCorrectDtoList() {
            // given
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace));
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willReturn(mock(WorkplaceMember.class));

            User requesterUser = mock(User.class);
            given(requesterUser.getName()).willReturn("요청자");
            given(requesterMember.getId()).willReturn(requesterMemberId);
            given(requesterMember.getUser()).willReturn(requesterUser);

            given(shift.getSchedule()).willReturn(schedule);
            given(schedule.getWorkplace()).willReturn(workplace);
            given(workplace.getId()).willReturn(workplaceId);
            given(workplace.getName()).willReturn("테스트 근무지");
            given(shift.getId()).willReturn(50L);
            given(shift.getStartAt()).willReturn(now.plusDays(1));
            given(shift.getEndAt()).willReturn(now.plusDays(1).plusHours(8));

            SubstituteRequest openRequest = mock(SubstituteRequest.class);
            given(openRequest.getId()).willReturn(1L);
            given(openRequest.getStatus()).willReturn(RequestStatus.OPEN);
            given(openRequest.getShift()).willReturn(shift);
            given(openRequest.getRequesterMember()).willReturn(requesterMember);
            given(openRequest.getCreateDate()).willReturn(now);

            SubstituteRequest acceptedRequest = mock(SubstituteRequest.class);
            given(acceptedRequest.getId()).willReturn(2L);
            given(acceptedRequest.getStatus()).willReturn(RequestStatus.ACCEPTED);
            given(acceptedRequest.getShift()).willReturn(shift);
            given(acceptedRequest.getRequesterMember()).willReturn(requesterMember);
            given(acceptedRequest.getCreateDate()).willReturn(now);

            given(substituteRequestRepository.findAllByWorkplaceId(eq(workplaceId), any()))
                .willReturn(List.of(openRequest, acceptedRequest));

            given(candidate.getRequest()).willReturn(acceptedRequest);
            given(candidate.getMember()).willReturn(candidateMember);
            given(candidateMember.getId()).willReturn(candidateUserId);
            given(candidateMember.getUser()).willReturn(candidateUser);
            given(candidateUser.getName()).willReturn("수락자");

            given(substituteCandidateRepository
                .findByRequestIdInAndStatus(List.of(2L), CandidateStatus.ACCEPTED))
                .willReturn(List.of(candidate));

            // when
            List<SubstituteRequestListResponse> result =
                substituteRequestService.list(workplaceId, actorId);

            // then
            assertThat(result).hasSize(2);

            SubstituteRequestListResponse openResponse = result.stream()
                .filter(r -> r.requestId().equals(1L)).findFirst().orElseThrow();
            assertThat(openResponse.acceptedMember()).isNull();

            SubstituteRequestListResponse acceptedResponse = result.stream()
                .filter(r -> r.requestId().equals(2L)).findFirst().orElseThrow();
            assertThat(acceptedResponse.acceptedMember()).isNotNull();
        }

        @Test
        @DisplayName("실패: ACCEPTED 요청인데 수락자가 없으면 예외가 발생한다.")
        void list_AcceptedWithoutCandidate_ThrowsException() {
            // given
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace));
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willReturn(mock(WorkplaceMember.class));

            SubstituteRequest acceptedRequest = mock(SubstituteRequest.class);
            given(acceptedRequest.getId()).willReturn(requestId);
            given(acceptedRequest.getStatus()).willReturn(RequestStatus.ACCEPTED);

            given(substituteRequestRepository.findAllByWorkplaceId(eq(workplaceId), any()))
                .willReturn(List.of(acceptedRequest));

            given(substituteCandidateRepository
                .findByRequestIdInAndStatus(List.of(requestId), CandidateStatus.ACCEPTED))
                .willReturn(List.of());

            // when & then
            assertBusinessError(
                () -> substituteRequestService.list(workplaceId, actorId),
                SubstituteRequestErrorCode.ACCEPTED_CANDIDATE_NOT_FOUND
            );
        }

        @Test
        @DisplayName("실패: ACCEPTED 요청에 수락자가 2명 이상이면 예외가 발생한다.")
        void list_AcceptedWithDuplicateCandidates_ThrowsException() {
            // given
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace));
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willReturn(mock(WorkplaceMember.class));

            SubstituteRequest acceptedRequest = mock(SubstituteRequest.class);
            given(acceptedRequest.getId()).willReturn(requestId);
            given(acceptedRequest.getStatus()).willReturn(RequestStatus.ACCEPTED);

            given(substituteRequestRepository.findAllByWorkplaceId(eq(workplaceId), any()))
                .willReturn(List.of(acceptedRequest));

            SubstituteCandidate candidate2 = mock(SubstituteCandidate.class);
            given(candidate.getRequest()).willReturn(acceptedRequest);
            given(candidate2.getRequest()).willReturn(acceptedRequest);

            given(substituteCandidateRepository
                .findByRequestIdInAndStatus(List.of(requestId), CandidateStatus.ACCEPTED))
                .willReturn(List.of(candidate, candidate2));

            // when & then
            assertBusinessError(
                () -> substituteRequestService.list(workplaceId, actorId),
                SubstituteRequestErrorCode.ACCEPTED_CANDIDATE_DUPLICATED
            );
        }

        @Test
        @DisplayName("실패: 근무지가 존재하지 않으면 "
            + "BusinessException(WORKPLACE_NOT_FOUND)이 발생한다.")
        void list_NotFoundWorkplace_ThrowsException() {
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.empty());

            assertBusinessError(
                () -> substituteRequestService.list(workplaceId, actorId),
                WorkplaceErrorCode.WORKPLACE_NOT_FOUND
            );
            verify(substituteRequestRepository, never()).findAllByWorkplaceId(any(), any());
        }

        @Test
        @DisplayName("실패: 매니저 권한이 없으면 예외가 발생한다.")
        void list_NotManager_ThrowsException() {
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace));
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willThrow(new BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED));

            assertBusinessError(
                () -> substituteRequestService.list(workplaceId, actorId),
                WorkplaceErrorCode.MANAGER_REQUIRED
            );
            // 권한이 없으면 요청 목록을 조회하지 않는다
            verify(substituteRequestRepository, never()).findAllByWorkplaceId(any(), any());
        }
    }

    // ===================== SUB-08 MANAGER 전체 종료 =====================

    @Nested
    @DisplayName("MANAGER 대타 요청 전체 종료 (close)")
    class CloseTest {

        @Test
        @DisplayName("성공: OPEN 상태의 요청을 MANAGER가 종료하면 CLOSED로 변경된다.")
        void close_OpenRequest_Success() {

            // given
            given(
                substituteRequestRepository
                    .findByIdForUpdate(requestId)
            ).willReturn(
                Optional.of(request)
            );

            given(request.getShift())
                .willReturn(shift);

            given(shift.getSchedule())
                .willReturn(schedule);

            given(schedule.getWorkplace())
                .willReturn(workplace);

            given(workplace.getId())
                .willReturn(workplaceId);

            given(request.getStatus())
                .willReturn(RequestStatus.OPEN);

            given(schedule.getStatus())
                .willReturn(ScheduleStatus.PUBLISHED);

            given(shift.getStatus())
                .willReturn(ShiftStatus.SCHEDULED);

            given(shift.getStartAt())
                .willReturn(now.plusDays(1));

            // 알림 수신자 설정 추가

            Long firstPendingMemberId = 20L;
            Long secondPendingMemberId = 30L;

            SubstituteCandidate firstPendingCandidate =
                mock(SubstituteCandidate.class);

            SubstituteCandidate secondPendingCandidate =
                mock(SubstituteCandidate.class);

            WorkplaceMember firstPendingMember =
                mock(WorkplaceMember.class);

            WorkplaceMember secondPendingMember =
                mock(WorkplaceMember.class);

            // 원래 대타 요청자
            given(request.getRequesterMember())
                .willReturn(requesterMember);

            given(requesterMember.getId())
                .willReturn(requesterMemberId);

            // 첫 번째 PENDING Candidate → Member #20
            given(firstPendingCandidate.getMember())
                .willReturn(firstPendingMember);

            given(firstPendingMember.getId())
                .willReturn(firstPendingMemberId);

            // 두 번째 PENDING Candidate → Member #30
            given(secondPendingCandidate.getMember())
                .willReturn(secondPendingMember);

            given(secondPendingMember.getId())
                .willReturn(secondPendingMemberId);

            /*
             * OPEN 상태이므로
             * 현재 PENDING Candidate만 조회한다.
             */
            given(
                substituteCandidateRepository
                    .findByRequestIdInAndStatus(
                        List.of(requestId),
                        CandidateStatus.PENDING
                    )
            ).willReturn(
                List.of(
                    firstPendingCandidate,
                    secondPendingCandidate
                )
            );

            // when
            SubstituteRequest result =
                substituteRequestService.close(
                    requestId,
                    actorId
                );

            // then
            assertThat(result)
                .isEqualTo(request);

            verify(substituteRequestRepository)
                .findByIdForUpdate(requestId);

            verify(workplaceMemberService)
                .requireManager(
                    actorId,
                    workplaceId
                );

            /*
             * OPEN 종료에서는 PENDING Candidate만
             * 조회했는지 확인
             */
            verify(substituteCandidateRepository)
                .findByRequestIdInAndStatus(
                    List.of(requestId),
                    CandidateStatus.PENDING
                );

            // Request 자체는 MANAGER_CLOSED 처리
            verify(request)
                .closeByManager(
                    any(LocalDateTime.class)
                );

            // Event 검증

            ArgumentCaptor<SubstituteRequestManagerClosedEvent>
                eventCaptor =
                ArgumentCaptor.forClass(
                    SubstituteRequestManagerClosedEvent.class
                );

            verify(applicationEventPublisher)
                .publishEvent(
                    eventCaptor.capture()
                );

            SubstituteRequestManagerClosedEvent event =
                eventCaptor.getValue();

            // 어떤 Request가 종료됐는가?
            assertThat(event.requestId())
                .isEqualTo(requestId);

            /*
             * 요청자 = 15
             * PENDING 후보 = 20, 30
             *
             * 최종 수신자는
             * [15, 20, 30]
             */
            assertThat(event.recipientMemberIds())
                .containsExactlyInAnyOrder(
                    requesterMemberId,
                    firstPendingMemberId,
                    secondPendingMemberId
                );
        }

        @Test
        @DisplayName("성공: ACCEPTED 상태의 요청도 MANAGER가 종료할 수 있다.")
        void close_AcceptedRequest_Success() {

            // given
            given(
                substituteRequestRepository
                    .findByIdForUpdate(requestId)
            ).willReturn(
                Optional.of(request)
            );

            given(request.getShift())
                .willReturn(shift);

            given(shift.getSchedule())
                .willReturn(schedule);

            given(schedule.getWorkplace())
                .willReturn(workplace);

            given(workplace.getId())
                .willReturn(workplaceId);

            given(request.getStatus())
                .willReturn(RequestStatus.ACCEPTED);

            given(schedule.getStatus())
                .willReturn(ScheduleStatus.PUBLISHED);

            given(shift.getStatus())
                .willReturn(ShiftStatus.SCHEDULED);

            given(shift.getStartAt())
                .willReturn(now.plusDays(1));

            // ACCEPTED 종료 알림 설정
            Long acceptedMemberId = 25L;

            // 요청자
            given(request.getRequesterMember())
                .willReturn(requesterMember);

            given(requesterMember.getId())
                .willReturn(requesterMemberId);

            /*
             * getAcceptedCandidates() 내부에서
             *
             * candidate.getRequest().getId()
             *
             * 를 사용하므로 둘 다 설정해야 한다.
             */
            given(request.getId())
                .willReturn(requestId);

            given(candidate.getRequest())
                .willReturn(request);

            // ACCEPTED Candidate의 WorkplaceMember
            given(candidate.getMember())
                .willReturn(candidateMember);

            given(candidateMember.getId())
                .willReturn(acceptedMemberId);

            /*
             * ACCEPTED 상태 종료에서는
             * ACCEPTED Candidate만 조회한다.
             */
            given(
                substituteCandidateRepository
                    .findByRequestIdInAndStatus(
                        List.of(requestId),
                        CandidateStatus.ACCEPTED
                    )
            ).willReturn(
                List.of(candidate)
            );

            // when
            SubstituteRequest result =
                substituteRequestService.close(
                    requestId,
                    actorId
                );

            // then
            assertThat(result)
                .isEqualTo(request);

            verify(request)
                .closeByManager(
                    any(LocalDateTime.class)
                );

            verify(substituteCandidateRepository)
                .findByRequestIdInAndStatus(
                    List.of(requestId),
                    CandidateStatus.ACCEPTED
                );

            // Event 검증
            ArgumentCaptor<SubstituteRequestManagerClosedEvent>
                eventCaptor =
                ArgumentCaptor.forClass(
                    SubstituteRequestManagerClosedEvent.class
                );

            verify(applicationEventPublisher)
                .publishEvent(
                    eventCaptor.capture()
                );

            SubstituteRequestManagerClosedEvent event =
                eventCaptor.getValue();

            assertThat(event.requestId())
                .isEqualTo(requestId);

            assertThat(event.recipientMemberIds())
                .containsExactlyInAnyOrder(
                    requesterMemberId,
                    acceptedMemberId
                );
        }

        @Test
        @DisplayName("실패: 존재하지 않는 요청이면 예외가 발생한다.")
        void close_RequestNotFound_ThrowsException() {
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.empty());

            assertBusinessError(
                () -> substituteRequestService.close(requestId, actorId),
                SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND
            );
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("실패: 실제 Workplace의 MANAGER가 아니면 예외가 발생한다.")
        void close_NotManager_ThrowsException() {
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));
            given(request.getShift()).willReturn(shift);
            given(shift.getSchedule()).willReturn(schedule);
            given(schedule.getWorkplace()).willReturn(workplace);
            given(workplace.getId()).willReturn(workplaceId);

            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willThrow(new BusinessException(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER));

            assertCloseFails(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER);
        }

        @Test
        @DisplayName("실패: 요청이 이미 APPROVED 상태면 예외가 발생한다.")
        void close_AlreadyApproved_ThrowsException() {
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));
            given(request.getShift()).willReturn(shift);
            given(shift.getSchedule()).willReturn(schedule);
            given(schedule.getWorkplace()).willReturn(workplace);
            given(workplace.getId()).willReturn(workplaceId);

            given(request.getStatus()).willReturn(RequestStatus.APPROVED);

            assertCloseFails(SubstituteRequestErrorCode.ALREADY_TERMINATED);
        }

        @Test
        @DisplayName("실패: 요청이 이미 CLOSED 상태면 예외가 발생한다.")
        void close_AlreadyClosed_ThrowsException() {
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));
            given(request.getShift()).willReturn(shift);
            given(shift.getSchedule()).willReturn(schedule);
            given(schedule.getWorkplace()).willReturn(workplace);
            given(workplace.getId()).willReturn(workplaceId);

            given(request.getStatus()).willReturn(RequestStatus.CLOSED);

            assertCloseFails(SubstituteRequestErrorCode.ALREADY_TERMINATED);
        }

        @Test
        @DisplayName("실패: Schedule이 PUBLISHED 상태가 아니면 예외가 발생한다.")
        void close_ScheduleNotPublished_ThrowsException() {
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));
            given(request.getShift()).willReturn(shift);
            given(shift.getSchedule()).willReturn(schedule);
            given(schedule.getWorkplace()).willReturn(workplace);
            given(workplace.getId()).willReturn(workplaceId);

            given(request.getStatus()).willReturn(RequestStatus.OPEN);
            given(schedule.getStatus()).willReturn(ScheduleStatus.DRAFT);

            assertCloseFails(ScheduleErrorCode.NOT_PUBLISHED);
        }

        @Test
        @DisplayName("실패: Shift가 SCHEDULED 상태가 아니면 예외가 발생한다.")
        void close_ShiftNotScheduled_ThrowsException() {
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));
            given(request.getShift()).willReturn(shift);
            given(shift.getSchedule()).willReturn(schedule);
            given(schedule.getWorkplace()).willReturn(workplace);
            given(workplace.getId()).willReturn(workplaceId);

            given(request.getStatus()).willReturn(RequestStatus.OPEN);
            given(schedule.getStatus()).willReturn(ScheduleStatus.PUBLISHED);
            given(shift.getStatus()).willReturn(ShiftStatus.CANCELLED);

            assertCloseFails(ShiftErrorCode.INVALID_STATUS_VALUE);
        }

        @Test
        @DisplayName("실패: Shift가 이미 시작된 경우(now >= startAt) 예외가 발생한다.")
        void close_ShiftAlreadyStarted_ThrowsException() {
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));
            given(request.getShift()).willReturn(shift);
            given(shift.getSchedule()).willReturn(schedule);
            given(schedule.getWorkplace()).willReturn(workplace);
            given(workplace.getId()).willReturn(workplaceId);

            given(request.getStatus()).willReturn(RequestStatus.OPEN);
            given(schedule.getStatus()).willReturn(ScheduleStatus.PUBLISHED);
            given(shift.getStatus()).willReturn(ShiftStatus.SCHEDULED);
            given(shift.getStartAt()).willReturn(now.minusMinutes(1));

            assertCloseFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED_NOT_CLOSE);
        }

        @Test
        @DisplayName("실패: Shift 시작 시각이 정확히 now와 같으면(경계값) 예외가 발생한다.")
        void close_ShiftStartsExactlyNow_ThrowsException() {
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request));
            given(request.getShift()).willReturn(shift);
            given(shift.getSchedule()).willReturn(schedule);
            given(schedule.getWorkplace()).willReturn(workplace);
            given(workplace.getId()).willReturn(workplaceId);

            given(request.getStatus()).willReturn(RequestStatus.OPEN);
            given(schedule.getStatus()).willReturn(ScheduleStatus.PUBLISHED);
            given(shift.getStatus()).willReturn(ShiftStatus.SCHEDULED);
            given(shift.getStartAt()).willReturn(now);

            assertCloseFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED_NOT_CLOSE);
        }

        @Test
        @DisplayName("성공: Candidate 상태는 변경되지 않는다 (요청만 CLOSED 처리).")
        void close_DoesNotModifyCandidateStatus() {

            // given
            given(
                substituteRequestRepository
                    .findByIdForUpdate(requestId)
            ).willReturn(
                Optional.of(request)
            );

            given(request.getShift())
                .willReturn(shift);

            given(shift.getSchedule())
                .willReturn(schedule);

            given(schedule.getWorkplace())
                .willReturn(workplace);

            given(workplace.getId())
                .willReturn(workplaceId);

            given(request.getStatus())
                .willReturn(RequestStatus.ACCEPTED);

            given(schedule.getStatus())
                .willReturn(ScheduleStatus.PUBLISHED);

            given(shift.getStatus())
                .willReturn(ShiftStatus.SCHEDULED);

            given(shift.getStartAt())
                .willReturn(now.plusDays(1));

            Long acceptedMemberId = 25L;

            // 요청자 Member 조회
            given(request.getRequesterMember())
                .willReturn(requesterMember);

            given(requesterMember.getId())
                .willReturn(requesterMemberId);

            // getAcceptedCandidates()가 Request ID를 사용함
            given(request.getId())
                .willReturn(requestId);

            given(candidate.getRequest())
                .willReturn(request);

            // ACCEPTED Candidate의 Member
            given(candidate.getMember())
                .willReturn(candidateMember);

            given(candidateMember.getId())
                .willReturn(acceptedMemberId);

            // ACCEPTED 상태 종료이므로 ACCEPTED Candidate 조회
            given(
                substituteCandidateRepository
                    .findByRequestIdInAndStatus(
                        List.of(requestId),
                        CandidateStatus.ACCEPTED
                    )
            ).willReturn(
                List.of(candidate)
            );

            // when
            substituteRequestService.close(
                requestId,
                actorId
            );

            // then

            // Manager 종료이므로 승인 처리는 하지 않는다.
            verify(request, never())
                .approveRequest(
                    any(),
                    any()
                );

            // Shift 담당자도 바꾸지 않는다.
            verify(shift, never())
                .changeMember(
                    any()
                );

            // Candidate를 새로 ACCEPTED 처리하지 않는다.
            verify(candidate, never())
                .acceptRequest(
                    any(LocalDateTime.class)
                );

            // Candidate를 REJECTED 처리하지도 않는다.
            verify(candidate, never())
                .reject(
                    any(LocalDateTime.class)
                );
        }
    }

    // ===================== SUB-01 대타 요청 생성 =====================

    @Nested
    @DisplayName("대타 요청 생성 (create)")
    class CreateTest {

        @Test
        @DisplayName("SUB-01 - 검증을 통과하면 대타 요청이 OPEN으로 저장된다")
        void create_Success_SavesAsOpen() {
            Shift newShift = createShift(
                publishedSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED
            );

            Long candidateMemberId = 30L;

            WorkplaceMember newCandidateMember =
                mock(WorkplaceMember.class);

            SubstituteCandidate createdCandidate =
                mock(SubstituteCandidate.class);

            given(newCandidateMember.getId())
                .willReturn(candidateMemberId);

            given(createdCandidate.getMember())
                .willReturn(newCandidateMember);

            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift));
            given(substituteRequestRepository
                .existsByShift_IdAndStatusIn(SHIFT_ID, ACTIVE_STATUSES))
                .willReturn(false);

            given(substituteCandidateService.findCandidates(
                newShift.getSchedule().getWorkplace().getId(),
                newShift.getMember().getId(),
                newShift
            )).willReturn(List.of(newCandidateMember));

            given(substituteRequestRepository.save(any(SubstituteRequest.class)))
                .willAnswer(invocation -> {
                    SubstituteRequest saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", SAVED_REQUEST_ID);
                    return saved;
                });

            given(substituteCandidateService.createCandidates(
                any(SubstituteRequest.class),
                any()
            ))
                .willReturn(
                    List.of(createdCandidate)
                );

            SubstituteRequestCreateResponse response =
                substituteRequestService.create(SHIFT_ID, REQUESTER_USER_ID);

            ArgumentCaptor<SubstituteRequest> captor =
                ArgumentCaptor.forClass(SubstituteRequest.class);
            verify(shiftRepository).findByIdForUpdate(SHIFT_ID);
            verify(substituteRequestRepository).save(captor.capture());
            SubstituteRequest saved = captor.getValue();
            assertThat(saved.getShift()).isSameAs(newShift);
            assertThat(saved.getRequesterMember()).isSameAs(newShift.getMember());
            assertThat(saved.getStatus()).isEqualTo(RequestStatus.OPEN);

            assertThat(response.requestCreated()).isTrue();
            assertThat(response.requestId()).isEqualTo(SAVED_REQUEST_ID);
            assertThat(response.shiftId()).isEqualTo(SHIFT_ID);
            assertThat(response.status()).isEqualTo(RequestStatus.OPEN);
            assertThat(response.candidateCount()).isEqualTo(1);

            ArgumentCaptor<SubstituteRequestCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                    SubstituteRequestCreatedEvent.class
                );

            verify(applicationEventPublisher)
                .publishEvent(
                    eventCaptor.capture()
                );

            SubstituteRequestCreatedEvent event =
                eventCaptor.getValue();

            assertThat(event.requestId())
                .isEqualTo(SAVED_REQUEST_ID);

            assertThat(event.candidateMemberIds())
                .containsExactly(candidateMemberId);
        }

        @Test
        @DisplayName("SUB-01 - 대타 후보가 없으면 Request를 생성하지 않는다")
        void create_NoCandidate_DoesNotCreateRequest() {

            // given
            Shift newShift = createShift(
                publishedSchedule(),
                REQUESTER_USER_ID,
                futureStartAt(),
                ShiftStatus.SCHEDULED
            );

            // 실제 DB에서 조회한 Shift/Workplace라면 ID가 존재하지만,
            // 테스트 Fixture는 직접 만든 Entity이므로 ID를 지정해준다.
            ReflectionTestUtils.setField(
                newShift,
                "id",
                SHIFT_ID
            );

            ReflectionTestUtils.setField(
                newShift.getSchedule().getWorkplace(),
                "id",
                workplaceId
            );

            Long managerMemberId = 30L;

            WorkplaceMember managerMember =
                mock(WorkplaceMember.class);

            given(managerMember.getId())
                .willReturn(managerMemberId);

            // 기존 Shift 조회
            given(
                shiftRepository.findByIdForUpdate(SHIFT_ID)
            ).willReturn(
                Optional.of(newShift)
            );

            // 기존 활성 Request 없음
            given(
                substituteRequestRepository
                    .existsByShift_IdAndStatusIn(
                        SHIFT_ID,
                        ACTIVE_STATUSES
                    )
            ).willReturn(false);

            // Candidate 없음
            given(
                substituteCandidateService.findCandidates(
                    newShift.getSchedule().getWorkplace().getId(),
                    newShift.getMember().getId(),
                    newShift
                )
            ).willReturn(
                List.of()
            );

            // Candidate가 없을 때 현재 MANAGER 조회
            given(
                workplaceMemberRepository
                    .findAllByWorkplace_IdAndRoleAndLeftAtIsNull(
                        workplaceId,
                        WorkplaceRole.MANAGER
                    )
            ).willReturn(
                List.of(managerMember)
            );

            // when
            SubstituteRequestCreateResponse response =
                substituteRequestService.create(
                    SHIFT_ID,
                    REQUESTER_USER_ID
                );

            // then - 기존 Response 검증
            assertThat(response.requestCreated())
                .isFalse();

            assertThat(response.requestId())
                .isNull();

            assertThat(response.shiftId())
                .isEqualTo(SHIFT_ID);

            assertThat(response.status())
                .isNull();

            assertThat(response.candidateCount())
                .isZero();

            // Request / Candidate는 여전히 생성되지 않음
            verify(substituteRequestRepository, never())
                .save(any(SubstituteRequest.class));

            verify(substituteCandidateService, never())
                .createCandidates(
                    any(SubstituteRequest.class),
                    any()
                );

            // 현재 MANAGER 조회가 수행됐는지 확인
            verify(workplaceMemberRepository)
                .findAllByWorkplace_IdAndRoleAndLeftAtIsNull(
                    workplaceId,
                    WorkplaceRole.MANAGER
                );

            // NoCandidate Event 검증
            ArgumentCaptor<SubstituteNoCandidateEvent> eventCaptor =
                ArgumentCaptor.forClass(
                    SubstituteNoCandidateEvent.class
                );

            verify(applicationEventPublisher)
                .publishEvent(
                    eventCaptor.capture()
                );

            SubstituteNoCandidateEvent event =
                eventCaptor.getValue();

            assertThat(event.shiftId())
                .isEqualTo(SHIFT_ID);

            assertThat(event.managerMemberIds())
                .containsExactly(managerMemberId);
        }

        @Test
        @DisplayName("SUB-01 실패 - 존재하지 않는 Shift")
        void create_Fail_ShiftNotFound() {
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.empty());

            assertCreateFails(SubstituteRequestErrorCode.SHIFT_NOT_FOUND);
        }

        @Test
        @DisplayName("SUB-01 실패 - 공개되지 않은 근무표의 Shift")
        void create_Fail_ScheduleNotPublished() {
            Shift newShift = createShift(
                draftSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED
            );
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift));

            assertCreateFails(SubstituteRequestErrorCode.SHIFT_NOT_PUBLISHED);
        }

        @Test
        @DisplayName("SUB-01 실패 - 취소된 Shift")
        void create_Fail_ShiftCancelled() {
            Shift newShift = createShift(
                publishedSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.CANCELLED
            );
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift));

            assertCreateFails(SubstituteRequestErrorCode.SHIFT_CANCELLED);
        }

        @Test
        @DisplayName("SUB-01 실패 - 본인의 Shift가 아님")
        void create_Fail_NotOwnShift() {
            Shift newShift = createShift(
                publishedSchedule(), OTHER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED
            );
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift));

            assertCreateFails(SubstituteRequestErrorCode.NOT_OWN_SHIFT);
        }

        @Test
        @DisplayName("SUB-01 실패 - 이미 시작된 Shift")
        void create_Fail_ShiftAlreadyStarted() {
            Shift newShift = createShift(
                publishedSchedule(),
                REQUESTER_USER_ID,
                now.minusMinutes(1),
                ShiftStatus.SCHEDULED
            );
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift));

            assertCreateFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED);
        }

        @Test
        @DisplayName("SUB-01 실패 - 이미 진행 중인(OPEN/ACCEPTED) 요청이 있음")
        void create_Fail_ActiveRequestExists() {
            Shift newShift = createShift(
                publishedSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED
            );
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift));
            given(substituteRequestRepository
                .existsByShift_IdAndStatusIn(SHIFT_ID, ACTIVE_STATUSES))
                .willReturn(true);

            assertCreateFails(SubstituteRequestErrorCode.ACTIVE_REQUEST_EXISTS);
        }

        private void assertCreateFails(SubstituteRequestErrorCode expected) {
            assertBusinessError(
                () -> substituteRequestService.create(SHIFT_ID, REQUESTER_USER_ID),
                expected
            );

            // 검증에서 막혔다면 후보 조회 → Request/Candidate 저장 → 알림까지 이어지면 안 된다
            verify(substituteCandidateService, never()).findCandidates(any(), any(), any());
            verify(substituteRequestRepository, never()).save(any(SubstituteRequest.class));
            verify(substituteCandidateService, never()).createCandidates(any(), any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }
    }

    // ===================== SUB-05 보낸 요청 목록 조회 =====================

    @Nested
    @DisplayName("보낸 대타 요청 목록 조회 (getSentRequests)")
    class SentRequestsTest {

        @Test
        @DisplayName("SUB-05 - 보낸 요청 목록을 최신순으로 반환하고, "
            + "만료된 요청은 CLOSED/EXPIRED로 표시된다")
        void getSentRequests_ReturnsLatestFirst_MarksExpired() {
            Shift openShift = createShift(
                publishedSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED
            );
            SubstituteRequest openRequest =
                new SubstituteRequest(openShift, openShift.getMember(), RequestStatus.OPEN);
            ReflectionTestUtils.setField(openRequest, "id", 1L);
            ReflectionTestUtils.setField(openRequest, "createDate", now.minusDays(1));

            Shift expiredShift = createShift(
                publishedSchedule(),
                REQUESTER_USER_ID,
                now.minusMinutes(1),
                ShiftStatus.SCHEDULED
            );
            SubstituteRequest expiredRequest =
                new SubstituteRequest(expiredShift, expiredShift.getMember(), RequestStatus.OPEN);
            ReflectionTestUtils.setField(expiredRequest, "id", 2L);
            ReflectionTestUtils.setField(expiredRequest, "createDate", now);

            given(substituteRequestRepository
                .findAllByRequesterMember_User_IdOrderByCreateDateDesc(REQUESTER_USER_ID))
                .willReturn(List.of(expiredRequest, openRequest));

            List<SentSubstituteRequestResponse> responses =
                substituteRequestService.getSentRequests(REQUESTER_USER_ID);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).requestId()).isEqualTo(2L);
            assertThat(responses.get(0).status()).isEqualTo(RequestStatus.CLOSED);
            assertThat(responses.get(0).closeReason()).isEqualTo(RequestCloseReason.EXPIRED);
            assertThat(responses.get(1).requestId()).isEqualTo(1L);
            assertThat(responses.get(1).status()).isEqualTo(RequestStatus.OPEN);
        }

        @Test
        @DisplayName("SUB-05 - 보낸 요청이 없으면 빈 리스트를 반환한다")
        void getSentRequests_NoRequests_ReturnsEmptyList() {
            given(substituteRequestRepository
                .findAllByRequesterMember_User_IdOrderByCreateDateDesc(REQUESTER_USER_ID))
                .willReturn(List.of());

            List<SentSubstituteRequestResponse> responses =
                substituteRequestService.getSentRequests(REQUESTER_USER_ID);

            assertThat(responses).isEmpty();
        }
    }

    // ===================== 공용 fixture 헬퍼 =====================

    private Shift createShift(
        Schedule scheduleFixture,
        Long ownerUserId,
        LocalDateTime startAt,
        ShiftStatus status
    ) {
        WorkplaceMember owner = new WorkplaceMember(
            scheduleFixture.getWorkplace(),
            new User(ownerUserId, "owner@example.com", "담당자"),
            WorkplaceRole.EMPLOYEE,
            now.minusDays(30),
            null
        );

        return new Shift(scheduleFixture, owner, startAt, startAt.plusHours(4), status);
    }

    private Schedule draftSchedule() {
        return new Schedule(new Workplace("SWITCH 카페", "INVITE01"), LocalDate.now(clock));
    }

    private Schedule publishedSchedule() {
        Schedule scheduleFixture = draftSchedule();
        scheduleFixture.publish(now);
        return scheduleFixture;
    }

    private LocalDateTime futureStartAt() {
        return now.plusDays(1);
    }
}