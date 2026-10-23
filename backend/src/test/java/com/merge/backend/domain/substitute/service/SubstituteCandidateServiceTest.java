package com.merge.backend.domain.substitute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.merge.backend.domain.shift.entity.Schedule;
import com.merge.backend.domain.shift.entity.ScheduleStatus;
import com.merge.backend.domain.shift.entity.Shift;
import com.merge.backend.domain.shift.entity.ShiftStatus;
import com.merge.backend.domain.shift.repository.ShiftRepository;
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository;
import com.merge.backend.domain.substitute.entity.CandidateStatus;
import com.merge.backend.domain.substitute.entity.RequestCloseReason;
import com.merge.backend.domain.substitute.entity.RequestStatus;
import com.merge.backend.domain.substitute.entity.SubstituteCandidate;
import com.merge.backend.domain.substitute.entity.SubstituteRequest;
import com.merge.backend.domain.substitute.event.SubstituteAllCandidatesRejectedEvent;
import com.merge.backend.domain.substitute.event.SubstituteCandidateAcceptedEvent;
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode;
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository;
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.user.service.UserService;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.global.exception.BusinessException;
import com.merge.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
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

/*
 * Entity(Request/Candidate/Shift...)는 mock이 아닌 실제 객체를 사용한다.
 * → verify(mock)가 아니라 "실제 상태가 바뀌었는지 / 실패 시 그대로인지"를 검증하기 위함
 */
@ExtendWith(MockitoExtension.class)
class SubstituteCandidateServiceTest {

    private static final Long WORKPLACE_ID = 100L;
    private static final Long SHIFT_ID = 300L;
    private static final Long REQUEST_ID = 50L;
    private static final Long CANDIDATE_ID = 1L;

    private static final Long REQUESTER_USER_ID = 10L;
    private static final Long CANDIDATE_USER_ID = 20L;
    private static final Long OTHER_USER_ID = 99L;

    private static final Long REQUESTER_MEMBER_ID = 15L;
    private static final Long CANDIDATE_MEMBER_ID = 25L;

    @Mock
    private WorkplaceMemberRepository workplaceMemberRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private UnavailableTimeRepository unavailableTimeRepository;

    @Mock
    private SubstituteCandidateRepository substituteCandidateRepository;

    @Mock
    private SubstituteRequestRepository substituteRequestRepository;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    // 2026-09-22 15:00 (KST)
    private final Clock clock = Clock.fixed(
        Instant.parse("2026-09-22T06:00:00Z"),
        ZoneId.of("Asia/Seoul")
    );

    private SubstituteCandidateService substituteCandidateService;

    private LocalDateTime now;
    private Workplace workplace;
    private Schedule schedule;
    private WorkplaceMember requesterMember;
    private WorkplaceMember candidateMember;
    private Shift shift;
    private SubstituteRequest request;
    private SubstituteCandidate candidate;

    @BeforeEach
    void setUp() {
        substituteCandidateService = new SubstituteCandidateService(
            workplaceMemberRepository,
            shiftRepository,
            unavailableTimeRepository,
            substituteCandidateRepository,
            substituteRequestRepository,
            userService,
            clock,
            applicationEventPublisher
        );

        now = LocalDateTime.now(clock);

        workplace = withId(new Workplace("SWITCH 카페", "1234"), WORKPLACE_ID);

        schedule = new Schedule(workplace, now.toLocalDate());
        schedule.publish(now.minusDays(1));

        requesterMember = member(REQUESTER_USER_ID, REQUESTER_MEMBER_ID, WorkplaceRole.EMPLOYEE);
        candidateMember = member(CANDIDATE_USER_ID, CANDIDATE_MEMBER_ID, WorkplaceRole.EMPLOYEE);

        // 내일 15:00 ~ 19:00 근무
        shift = withId(
            new Shift(
                schedule,
                requesterMember,
                now.plusDays(1),
                now.plusDays(1).plusHours(4),
                ShiftStatus.SCHEDULED
            ),
            SHIFT_ID
        );

        request = withId(
            new SubstituteRequest(shift, requesterMember, RequestStatus.OPEN),
            REQUEST_ID
        );

        candidate = withId(new SubstituteCandidate(request, candidateMember), CANDIDATE_ID);
    }

    // ===================== SUB-01 후보자 필터링 =====================

    @Nested
    @DisplayName("대타 후보자 조회 (findCandidates)")
    class FindCandidatesTest {

        @Test
        @DisplayName("요청자/MANAGER/공식 근무 충돌/근무 불가 시간/다른 대타 약속이 있는 멤버는 제외된다")
        void findCandidates_FiltersIneligibleMembers() {

            // given
            WorkplaceMember manager = member(31L, 131L, WorkplaceRole.MANAGER);
            WorkplaceMember freeEmployee = member(32L, 132L, WorkplaceRole.EMPLOYEE);
            WorkplaceMember shiftConflict = member(33L, 133L, WorkplaceRole.EMPLOYEE);
            WorkplaceMember unavailable = member(34L, 134L, WorkplaceRole.EMPLOYEE);
            WorkplaceMember activeSubstitute = member(35L, 135L, WorkplaceRole.EMPLOYEE);

            given(workplaceMemberRepository.findAllByWorkplace_IdAndLeftAtIsNull(WORKPLACE_ID))
                .willReturn(List.of(
                    requesterMember, manager, freeEmployee,
                    shiftConflict, unavailable, activeSubstitute
                ));

            // 같은 메서드가 멤버마다 호출되므로 userId에 따라 결과를 돌려준다
            given(shiftRepository.existsOverlappingOfficialShift(
                anyLong(), eq(ScheduleStatus.PUBLISHED),
                eq(shift.getStartAt()), eq(shift.getEndAt()), eq(SHIFT_ID)
            )).willAnswer(invocation -> invocation.getArgument(0).equals(33L));

            given(unavailableTimeRepository.existsOverlappingUnavailableTime(
                anyLong(), eq(shift.getStartAt()), eq(shift.getEndAt())
            )).willAnswer(invocation -> invocation.getArgument(0).equals(34L));

            given(substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                anyLong(), eq(shift.getStartAt()), eq(shift.getEndAt()), eq(now)
            )).willAnswer(invocation -> invocation.getArgument(0).equals(35L));

            // when
            List<WorkplaceMember> result = substituteCandidateService.findCandidates(
                WORKPLACE_ID, REQUESTER_MEMBER_ID, shift
            );

            // then
            assertThat(result).containsExactly(freeEmployee);
        }

        @Test
        @DisplayName("조건을 만족하는 멤버가 없으면 빈 리스트를 반환한다")
        void findCandidates_NoEligibleMember_ReturnsEmpty() {

            // given - 요청자 본인만 남아 있는 근무지
            given(workplaceMemberRepository.findAllByWorkplace_IdAndLeftAtIsNull(WORKPLACE_ID))
                .willReturn(List.of(requesterMember));

            // when
            List<WorkplaceMember> result = substituteCandidateService.findCandidates(
                WORKPLACE_ID, REQUESTER_MEMBER_ID, shift
            );

            // then
            assertThat(result).isEmpty();
        }
    }

    // ===================== SUB-02 / SUB-07 조회 =====================

    @Nested
    @DisplayName("받은 요청 / 수락 대기 요청 조회")
    class FindRequestsTest {

        @Test
        @DisplayName("SUB-02 - 현재 사용자 ID와 현재 시각으로 받은 요청을 조회한다")
        void findReceivedRequests_Success() {

            // given
            given(substituteCandidateRepository.findReceivedRequests(CANDIDATE_USER_ID, now))
                .willReturn(List.of(candidate));

            // when
            List<SubstituteCandidate> result =
                substituteCandidateService.findReceivedRequests(CANDIDATE_USER_ID);

            // then
            assertThat(result).containsExactly(candidate);
        }

        @Test
        @DisplayName("SUB-07 - 현재 사용자 ID와 현재 시각으로 승인 대기 요청을 조회한다")
        void findAcceptedPendingRequests_Success() {

            // given
            given(substituteCandidateRepository.findAcceptedPendingRequests(
                CANDIDATE_USER_ID, now
            )).willReturn(List.of(candidate));

            // when
            List<SubstituteCandidate> result =
                substituteCandidateService.findAcceptedPendingRequests(CANDIDATE_USER_ID);

            // then
            assertThat(result).containsExactly(candidate);
        }
    }

    // ===================== SUB-03 수락 =====================

    @Nested
    @DisplayName("대타 요청 수락 (acceptCandidate)")
    class AcceptTest {

        @Test
        @DisplayName("성공 - Candidate와 Request가 ACCEPTED로 바뀌고 MANAGER에게 이벤트가 발행된다")
        void acceptCandidate_Success() {

            // given
            givenRespondableCandidate();

            WorkplaceMember manager = member(40L, 140L, WorkplaceRole.MANAGER);
            given(workplaceMemberRepository.findAllByWorkplace_IdAndRoleAndLeftAtIsNull(
                WORKPLACE_ID, WorkplaceRole.MANAGER
            )).willReturn(List.of(manager));

            // when
            SubstituteCandidate result =
                substituteCandidateService.acceptCandidate(CANDIDATE_ID, CANDIDATE_USER_ID);

            // then - 실제 상태 변경
            assertThat(result).isSameAs(candidate);
            assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.ACCEPTED);
            assertThat(candidate.getRespondedAt()).isEqualTo(now);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.ACCEPTED);

            // then - Shift 담당자는 MANAGER 승인 전까지 바뀌지 않는다
            assertThat(shift.getMember()).isSameAs(requesterMember);

            // then - Request Lock → User Lock 순서
            InOrder lockOrder = inOrder(substituteRequestRepository, userService);
            lockOrder.verify(substituteRequestRepository).findByIdForUpdate(REQUEST_ID);
            lockOrder.verify(userService).getByIdForUpdate(CANDIDATE_USER_ID);

            // then - 후보자 기준으로 시간 충돌을 검사했는지
            verify(shiftRepository).existsOverlappingOfficialShift(
                CANDIDATE_USER_ID, ScheduleStatus.PUBLISHED,
                shift.getStartAt(), shift.getEndAt(), SHIFT_ID
            );
            verify(unavailableTimeRepository).existsOverlappingUnavailableTime(
                CANDIDATE_USER_ID, shift.getStartAt(), shift.getEndAt()
            );
            verify(substituteCandidateRepository).existsOverlappingAcceptedSubstitute(
                CANDIDATE_USER_ID, shift.getStartAt(), shift.getEndAt(), now
            );

            // then - 이벤트
            ArgumentCaptor<SubstituteCandidateAcceptedEvent> eventCaptor =
                ArgumentCaptor.forClass(SubstituteCandidateAcceptedEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

            assertThat(eventCaptor.getValue().requestId()).isEqualTo(REQUEST_ID);
            assertThat(eventCaptor.getValue().managerMemberIds()).containsExactly(140L);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 Candidate")
        void acceptCandidate_CandidateNotFound() {

            // given
            given(substituteCandidateRepository.findRequestIdById(CANDIDATE_ID))
                .willReturn(Optional.empty());

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.CANDIDATE_NOT_FOUND);
            verify(substituteRequestRepository, never()).findByIdForUpdate(anyLong());
        }

        @Test
        @DisplayName("실패 - Candidate가 가리키는 Request가 존재하지 않음")
        void acceptCandidate_RequestNotFound() {

            // given
            given(substituteCandidateRepository.findRequestIdById(CANDIDATE_ID))
                .willReturn(Optional.of(REQUEST_ID));
            given(substituteRequestRepository.findByIdForUpdate(REQUEST_ID))
                .willReturn(Optional.empty());

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND);
        }

        @Test
        @DisplayName("실패 - 다른 사람의 Candidate를 수락할 수 없다")
        void acceptCandidate_NotOwnCandidate() {

            // given
            givenRespondableCandidate();

            // when & then
            assertFailsWithoutSideEffect(
                () -> substituteCandidateService.acceptCandidate(CANDIDATE_ID, OTHER_USER_ID),
                SubstituteRequestErrorCode.NOT_OWN_CANDIDATE
            );
        }

        @Test
        @DisplayName("실패 - 퇴사한 후보자는 수락할 수 없다")
        void acceptCandidate_LeftMember() {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(candidateMember, "leftAt", now.minusHours(1));

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER);
        }

        @Test
        @DisplayName("실패 - 후보자가 더 이상 EMPLOYEE가 아니면 수락할 수 없다")
        void acceptCandidate_NotEmployee() {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(candidateMember, "role", WorkplaceRole.MANAGER);

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER);
        }

        @ParameterizedTest(name = "Candidate 상태 = {0}")
        @EnumSource(value = CandidateStatus.class, names = {"ACCEPTED", "REJECTED"})
        @DisplayName("실패 - 이미 응답한 Candidate는 다시 수락할 수 없다")
        void acceptCandidate_AlreadyResponded(CandidateStatus status) {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(candidate, "status", status);

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.CANDIDATE_ALREADY_RESPONDED);
        }

        @ParameterizedTest(name = "Request 상태 = {0}")
        @EnumSource(value = RequestStatus.class, names = {"ACCEPTED", "APPROVED", "CLOSED"})
        @DisplayName("실패 - Request가 OPEN이 아니면 수락할 수 없다")
        void acceptCandidate_RequestNotOpen(RequestStatus status) {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(request, "status", status);

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.REQUEST_NOT_OPEN);
        }

        @Test
        @DisplayName("실패 - 공개되지 않은 근무표의 Shift")
        void acceptCandidate_ScheduleNotPublished() {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(schedule, "status", ScheduleStatus.DRAFT);

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.SHIFT_NOT_PUBLISHED);
        }

        @Test
        @DisplayName("실패 - 취소된 Shift")
        void acceptCandidate_ShiftCancelled() {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(shift, "status", ShiftStatus.CANCELLED);

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.SHIFT_CANCELLED);
        }

        @Test
        @DisplayName("실패 - 이미 시작된 Shift (startAt < now)")
        void acceptCandidate_ShiftAlreadyStarted() {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(shift, "startAt", now.minusMinutes(1));

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED);
        }

        @Test
        @DisplayName("실패 - Shift 시작 시각이 정확히 now (경계값)")
        void acceptCandidate_ShiftStartsExactlyNow() {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(shift, "startAt", now);

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED);
        }

        @Test
        @DisplayName("실패 - 다른 공식 근무와 시간이 겹침")
        void acceptCandidate_OfficialShiftConflict() {

            // given
            givenRespondableCandidate();
            given(shiftRepository.existsOverlappingOfficialShift(
                CANDIDATE_USER_ID, ScheduleStatus.PUBLISHED,
                shift.getStartAt(), shift.getEndAt(), SHIFT_ID
            )).willReturn(true);

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.CONFLICT_SHIFT);
        }

        @Test
        @DisplayName("실패 - 근무 불가 시간과 겹침")
        void acceptCandidate_UnavailableTimeConflict() {

            // given
            givenRespondableCandidate();
            given(unavailableTimeRepository.existsOverlappingUnavailableTime(
                CANDIDATE_USER_ID, shift.getStartAt(), shift.getEndAt()
            )).willReturn(true);

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME);
        }

        @Test
        @DisplayName("실패 - 이미 수락한 다른 대타 약속과 겹침")
        void acceptCandidate_ActiveSubstituteConflict() {

            // given
            givenRespondableCandidate();
            given(substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                CANDIDATE_USER_ID, shift.getStartAt(), shift.getEndAt(), now
            )).willReturn(true);

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE);
        }

        private void assertAcceptFails(ErrorCode expected) {
            assertFailsWithoutSideEffect(
                () -> substituteCandidateService.acceptCandidate(CANDIDATE_ID, CANDIDATE_USER_ID),
                expected
            );
        }
    }

    // ===================== SUB-03 거절 =====================

    @Nested
    @DisplayName("대타 요청 거절 (rejectCandidate)")
    class RejectTest {

        @Test
        @DisplayName("성공 - 다른 PENDING 후보가 남아 있으면 Request는 OPEN을 유지한다")
        void rejectCandidate_OtherPendingCandidateExists() {

            // given
            givenRespondableCandidate();
            given(substituteCandidateRepository.existsByRequest_IdAndStatusAndIdNot(
                REQUEST_ID, CandidateStatus.PENDING, CANDIDATE_ID
            )).willReturn(true);

            // when
            substituteCandidateService.rejectCandidate(CANDIDATE_ID, CANDIDATE_USER_ID);

            // then
            assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.REJECTED);
            assertThat(candidate.getRespondedAt()).isEqualTo(now);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
            assertThat(request.getCloseReason()).isNull();
            assertThat(request.getClosedAt()).isNull();

            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("성공 - 마지막 후보가 거절하면 Request가 CLOSED(ALL_CANDIDATES_REJECTED) 된다")
        void rejectCandidate_LastCandidate_ClosesRequest() {

            // given
            givenRespondableCandidate();
            given(substituteCandidateRepository.existsByRequest_IdAndStatusAndIdNot(
                REQUEST_ID, CandidateStatus.PENDING, CANDIDATE_ID
            )).willReturn(false);

            // 요청자가 동시에 MANAGER 목록에 포함된 경우 → 알림 수신자 중복 제거 확인
            WorkplaceMember otherManager = member(41L, 141L, WorkplaceRole.MANAGER);
            given(workplaceMemberRepository.findAllByWorkplace_IdAndRoleAndLeftAtIsNull(
                WORKPLACE_ID, WorkplaceRole.MANAGER
            )).willReturn(List.of(requesterMember, otherManager));

            // when
            substituteCandidateService.rejectCandidate(CANDIDATE_ID, CANDIDATE_USER_ID);

            // then
            assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.REJECTED);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.CLOSED);
            assertThat(request.getCloseReason())
                .isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED);
            assertThat(request.getClosedAt()).isEqualTo(now);

            ArgumentCaptor<SubstituteAllCandidatesRejectedEvent> eventCaptor =
                ArgumentCaptor.forClass(SubstituteAllCandidatesRejectedEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

            assertThat(eventCaptor.getValue().requestId()).isEqualTo(REQUEST_ID);
            assertThat(eventCaptor.getValue().recipientMemberIds())
                .containsExactlyInAnyOrder(REQUESTER_MEMBER_ID, 141L);
        }

        @Test
        @DisplayName("실패 - 다른 사람의 Candidate를 거절할 수 없다")
        void rejectCandidate_NotOwnCandidate() {

            // given
            givenRespondableCandidate();

            // when & then
            assertFailsWithoutSideEffect(
                () -> substituteCandidateService.rejectCandidate(CANDIDATE_ID, OTHER_USER_ID),
                SubstituteRequestErrorCode.NOT_OWN_CANDIDATE
            );
        }

        @Test
        @DisplayName("실패 - 퇴사한 후보자는 거절할 수 없다")
        void rejectCandidate_LeftMember() {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(candidateMember, "leftAt", now.minusHours(1));

            // when & then
            assertRejectFails(SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER);
        }

        @Test
        @DisplayName("실패 - 이미 응답한 Candidate는 다시 거절할 수 없다")
        void rejectCandidate_AlreadyResponded() {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(candidate, "status", CandidateStatus.REJECTED);

            // when & then
            assertRejectFails(SubstituteRequestErrorCode.CANDIDATE_ALREADY_RESPONDED);
        }

        @Test
        @DisplayName("실패 - 다른 후보가 이미 수락한 Request는 거절할 수 없다")
        void rejectCandidate_RequestNotOpen() {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(request, "status", RequestStatus.ACCEPTED);

            // when & then
            assertRejectFails(SubstituteRequestErrorCode.REQUEST_NOT_OPEN);
        }

        @Test
        @DisplayName("실패 - 이미 시작된 Shift는 거절할 수 없다")
        void rejectCandidate_ShiftAlreadyStarted() {

            // given
            givenRespondableCandidate();
            ReflectionTestUtils.setField(shift, "startAt", now);

            // when & then
            assertRejectFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED);
        }

        private void assertRejectFails(ErrorCode expected) {
            assertFailsWithoutSideEffect(
                () -> substituteCandidateService.rejectCandidate(CANDIDATE_ID, CANDIDATE_USER_ID),
                expected
            );

            // 실패했다면 "남은 후보 확인 → Request 종료" 단계까지 가면 안 된다
            verify(substituteCandidateRepository, never())
                .existsByRequest_IdAndStatusAndIdNot(any(), any(), any());
        }
    }

    // ===================== 공용 헬퍼 =====================

    // Request Lock → Candidate 조회까지 정상 동작하도록 설정
    private void givenRespondableCandidate() {
        given(substituteCandidateRepository.findRequestIdById(CANDIDATE_ID))
            .willReturn(Optional.of(REQUEST_ID));
        given(substituteRequestRepository.findByIdForUpdate(REQUEST_ID))
            .willReturn(Optional.of(request));
        given(substituteCandidateRepository.findById(CANDIDATE_ID))
            .willReturn(Optional.of(candidate));
    }

    // 지정한 에러코드로 실패하고, 상태 변경 / 이벤트 발행이 일어나지 않았는지 확인
    private void assertFailsWithoutSideEffect(ThrowingCallable action, ErrorCode expected) {
        CandidateStatus candidateStatusBefore = candidate.getStatus();
        RequestStatus requestStatusBefore = request.getStatus();

        assertThatThrownBy(action)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(expected);

        assertThat(candidate.getStatus()).isEqualTo(candidateStatusBefore);
        assertThat(candidate.getRespondedAt()).isNull();
        assertThat(request.getStatus()).isEqualTo(requestStatusBefore);
        assertThat(request.getClosedAt()).isNull();
        assertThat(shift.getMember()).isSameAs(requesterMember);

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    private WorkplaceMember member(Long userId, Long memberId, WorkplaceRole role) {
        User user = new User(userId, "user" + userId + "@test.com", "user" + userId);
        return withId(
            new WorkplaceMember(workplace, user, role, now.minusDays(30)),
            memberId
        );
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
