package com.merge.backend.domain.substitute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.merge.backend.domain.shift.entity.Schedule;
import com.merge.backend.domain.shift.entity.Shift;
import com.merge.backend.domain.shift.entity.ShiftStatus;
import com.merge.backend.domain.shift.entity.UnavailableTime;
import com.merge.backend.domain.shift.exception.ShiftErrorCode;
import com.merge.backend.domain.shift.repository.ScheduleRepository;
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
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode;
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository;
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.user.repository.UserRepository;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.domain.workplace.repository.WorkplaceRepository;
import com.merge.backend.global.config.TestClock;
import com.merge.backend.global.config.TestTimeConfig;
import com.merge.backend.global.exception.BusinessException;
import com.merge.backend.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/*
 * mock 없이 실제 DB(MySQL Testcontainers)로 대타 요청 전체 흐름을 검증한다.
 * 각 단계 후 flush/clear 하고 DB에서 다시 조회해 "실제로 저장된 값"을 확인한다.
 * 테스트 종료 시 롤백된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig.class)
@Transactional
class SubstituteFlowIntegrationTest {

    // 2026-09-15 10:00 (KST)
    private static final Instant FIXED_NOW = Instant.parse("2026-09-15T01:00:00Z");

    @Autowired
    private SubstituteRequestService substituteRequestService;

    @Autowired
    private SubstituteCandidateService substituteCandidateService;

    @Autowired
    private SubstituteRequestRepository substituteRequestRepository;

    @Autowired
    private SubstituteCandidateRepository substituteCandidateRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private UnavailableTimeRepository unavailableTimeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkplaceRepository workplaceRepository;

    @Autowired
    private WorkplaceMemberRepository workplaceMemberRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestClock testClock;

    private LocalDateTime now;
    private Workplace workplace;
    private Schedule schedule;
    private WorkplaceMember manager;
    private WorkplaceMember requester;
    private WorkplaceMember candidateA;
    private WorkplaceMember candidateB;
    private Shift shift;

    @BeforeEach
    void setUp() {
        testClock.setInstant(FIXED_NOW);
        now = LocalDateTime.now(testClock);

        workplace = workplaceRepository.save(new Workplace("대타 흐름 테스트 매장", "FLOW0001"));

        schedule = new Schedule(
            workplace,
            now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        );
        schedule.publish(now.minusDays(1));
        schedule = scheduleRepository.save(schedule);

        manager = saveMember("manager", WorkplaceRole.MANAGER);
        requester = saveMember("requester", WorkplaceRole.EMPLOYEE);
        candidateA = saveMember("candidate-a", WorkplaceRole.EMPLOYEE);
        candidateB = saveMember("candidate-b", WorkplaceRole.EMPLOYEE);

        // 이틀 뒤 10:00 ~ 14:00
        shift = saveShift(requester, now.plusDays(2).withHour(10), 4);

        flushAndClear();
    }

    // ===================== 전체 흐름 =====================

    @Nested
    @DisplayName("전체 흐름")
    class FullFlowTest {

        @Test
        @DisplayName("요청 → 수락 → 승인 → Shift 담당자가 수락자로 변경된다")
        void requestAcceptApprove_ChangesShiftMember() {

            // 1. 요청 생성 - MANAGER와 요청자를 제외한 2명이 후보가 된다
            SubstituteRequestCreateResponse created =
                substituteRequestService.create(shift.getId(), userId(requester));

            assertThat(created.requestCreated()).isTrue();
            assertThat(created.status()).isEqualTo(RequestStatus.OPEN);
            assertThat(created.candidateCount()).isEqualTo(2);

            Long requestId = created.requestId();
            flushAndClear();

            assertThat(candidatesOf(requestId))
                .extracting(candidate -> candidate.getMember().getId())
                .containsExactlyInAnyOrder(candidateA.getId(), candidateB.getId());

            // 2. 후보자 A가 받은 요청 목록에서 확인
            assertThat(substituteCandidateService.findReceivedRequests(userId(candidateA)))
                .extracting(candidate -> candidate.getRequest().getId())
                .containsExactly(requestId);

            // 3. 후보자 A 수락
            substituteCandidateService.acceptCandidate(
                candidateIdOf(requestId, candidateA), userId(candidateA)
            );
            flushAndClear();

            assertThat(findRequest(requestId).getStatus()).isEqualTo(RequestStatus.ACCEPTED);
            assertThat(findCandidate(requestId, candidateA).getStatus())
                .isEqualTo(CandidateStatus.ACCEPTED);
            assertThat(findCandidate(requestId, candidateA).getRespondedAt()).isEqualTo(now);

            // 아직 승인 전이므로 Shift 담당자는 그대로
            assertThat(findShift().getMember().getId()).isEqualTo(requester.getId());

            // 수락 이후: A는 승인 대기 목록에, B의 받은 요청 목록에서는 사라진다
            assertThat(substituteCandidateService.findAcceptedPendingRequests(userId(candidateA)))
                .hasSize(1);
            assertThat(substituteCandidateService.findReceivedRequests(userId(candidateB)))
                .isEmpty();

            // 4. MANAGER 목록 조회 - 수락자 정보가 함께 내려온다
            List<SubstituteRequestListResponse> list =
                substituteRequestService.list(workplace.getId(), userId(manager));

            assertThat(list).singleElement().satisfies(response -> {
                assertThat(response.requestId()).isEqualTo(requestId);
                assertThat(response.status()).isEqualTo(RequestStatus.ACCEPTED);
                assertThat(response.acceptedMember().memberId()).isEqualTo(candidateA.getId());
            });

            // 5. MANAGER 승인
            substituteRequestService.approve(requestId, userId(manager));
            flushAndClear();

            // 6. DB에서 다시 조회해 실제 변경 확인
            SubstituteRequest approved = findRequest(requestId);
            assertThat(approved.getStatus()).isEqualTo(RequestStatus.APPROVED);
            assertThat(approved.getApprovedAt()).isEqualTo(now);
            assertThat(approved.getClosedAt()).isEqualTo(now);

            assertThat(findShift().getMember().getId()).isEqualTo(candidateA.getId());

            assertThat(findCandidate(requestId, candidateB).getStatus())
                .isEqualTo(CandidateStatus.PENDING);

            // 요청자의 보낸 요청 목록에는 APPROVED로 보인다
            assertThat(substituteRequestService.getSentRequests(userId(requester)))
                .extracting(SentSubstituteRequestResponse::status)
                .containsExactly(RequestStatus.APPROVED);

            // 이제 이 Shift는 A의 근무이므로 원래 요청자는 다시 요청할 수 없다
            assertBusinessError(
                () -> substituteRequestService.create(shift.getId(), userId(requester)),
                SubstituteRequestErrorCode.NOT_OWN_SHIFT
            );
        }

        @Test
        @DisplayName("후보 전원이 거절하면 CLOSED 되고, 같은 Shift로 다시 요청할 수 있다")
        void allCandidatesReject_ClosesRequest_ThenCanRequestAgain() {

            // given
            Long requestId = createRequest();

            // when
            substituteCandidateService.rejectCandidate(
                candidateIdOf(requestId, candidateA), userId(candidateA)
            );
            flushAndClear();

            // then - 한 명 남았으므로 아직 OPEN
            assertThat(findRequest(requestId).getStatus()).isEqualTo(RequestStatus.OPEN);

            // when - 마지막 후보도 거절
            substituteCandidateService.rejectCandidate(
                candidateIdOf(requestId, candidateB), userId(candidateB)
            );
            flushAndClear();

            // then
            SubstituteRequest closed = findRequest(requestId);
            assertThat(closed.getStatus()).isEqualTo(RequestStatus.CLOSED);
            assertThat(closed.getCloseReason())
                .isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED);
            assertThat(closed.getClosedAt()).isEqualTo(now);

            assertThat(findShift().getMember().getId()).isEqualTo(requester.getId());

            // 활성 요청이 없으므로 다시 요청 가능
            SubstituteRequestCreateResponse retried =
                substituteRequestService.create(shift.getId(), userId(requester));

            assertThat(retried.requestCreated()).isTrue();
            assertThat(retried.requestId()).isNotEqualTo(requestId);
        }

        @Test
        @DisplayName("수락 후 MANAGER가 종료하면 Shift 담당자는 그대로이고, 이후 승인할 수 없다")
        void acceptThenManagerClose_ShiftUnchanged_CannotApprove() {

            // given
            Long requestId = createRequest();
            substituteCandidateService.acceptCandidate(
                candidateIdOf(requestId, candidateA), userId(candidateA)
            );
            flushAndClear();

            // when
            substituteRequestService.close(requestId, userId(manager));
            flushAndClear();

            // then
            SubstituteRequest closed = findRequest(requestId);
            assertThat(closed.getStatus()).isEqualTo(RequestStatus.CLOSED);
            assertThat(closed.getCloseReason()).isEqualTo(RequestCloseReason.MANAGER_CLOSED);
            assertThat(findShift().getMember().getId()).isEqualTo(requester.getId());

            assertBusinessError(
                () -> substituteRequestService.approve(requestId, userId(manager)),
                SubstituteRequestErrorCode.INVALID_REQUEST
            );
        }
    }

    // ===================== 수락 / 거절 검증 =====================

    @Nested
    @DisplayName("수락 / 거절 실패 시 DB가 바뀌지 않는다")
    class RespondValidationTest {

        @Test
        @DisplayName("요청 이후 등록한 근무 불가 시간과 겹치면 수락할 수 없다")
        void cannotAcceptWhenUnavailableTimeOverlaps() {

            // given
            Long requestId = createRequest();
            unavailableTimeRepository.save(new UnavailableTime(
                userRepository.findById(userId(candidateA)).orElseThrow(),
                shift.getStartAt().plusHours(1),
                shift.getEndAt().plusHours(1)
            ));
            flushAndClear();

            // when & then
            assertBusinessError(
                () -> substituteCandidateService.acceptCandidate(
                    candidateIdOf(requestId, candidateA), userId(candidateA)
                ),
                SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME
            );

            assertUntouched(requestId);
        }

        @Test
        @DisplayName("이미 수락한 다른 대타 약속과 시간이 겹치면 수락할 수 없다")
        void cannotAcceptWhenOtherSubstituteOverlaps() {

            // given - 같은 시간대에 다른 직원의 근무와 대타 요청이 하나 더 있다
            WorkplaceMember otherRequester = saveMember("other-requester", WorkplaceRole.EMPLOYEE);
            Shift otherShift = saveShift(otherRequester, shift.getStartAt().plusHours(2), 4);
            flushAndClear();

            Long requestId = createRequest();
            Long otherRequestId =
                substituteRequestService.create(otherShift.getId(), userId(otherRequester))
                    .requestId();
            flushAndClear();

            // A가 다른 요청을 먼저 수락
            substituteCandidateService.acceptCandidate(
                candidateIdOf(otherRequestId, candidateA), userId(candidateA)
            );
            flushAndClear();

            // when & then
            assertBusinessError(
                () -> substituteCandidateService.acceptCandidate(
                    candidateIdOf(requestId, candidateA), userId(candidateA)
                ),
                SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE
            );

            assertUntouched(requestId);
        }
    }

    // ===================== 승인 검증 =====================

    @Nested
    @DisplayName("승인 실패 시 Shift 담당자가 바뀌지 않는다")
    class ApproveValidationTest {

        @Test
        @DisplayName("수락한 사람이 퇴사하면 승인으로 이어지지 않는다")
        void cannotApproveWhenAcceptedMemberLeft() {

            // given
            Long requestId = acceptedRequestByA();
            leave(candidateA);

            // when & then
            assertBusinessError(
                () -> substituteRequestService.approve(requestId, userId(manager)),
                ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE
            );

            assertNotApproved(requestId);
        }

        private Long acceptedRequestByA() {
            Long requestId = createRequest();
            substituteCandidateService.acceptCandidate(
                candidateIdOf(requestId, candidateA), userId(candidateA)
            );
            flushAndClear();
            return requestId;
        }

        private void assertNotApproved(Long requestId) {
            flushAndClear();
            assertThat(findRequest(requestId).getStatus()).isEqualTo(RequestStatus.ACCEPTED);
            assertThat(findRequest(requestId).getApprovedAt()).isNull();
            assertThat(findShift().getMember().getId()).isEqualTo(requester.getId());
        }
    }

    // ===================== 헬퍼 =====================

    private Long createRequest() {
        Long requestId = substituteRequestService.create(shift.getId(), userId(requester))
            .requestId();
        flushAndClear();
        return requestId;
    }

    // 실패한 요청이 Request / Candidate / Shift 어느 것도 바꾸지 않았는지 DB 기준으로 확인
    private void assertUntouched(Long requestId) {
        flushAndClear();

        SubstituteRequest request = findRequest(requestId);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(request.getClosedAt()).isNull();

        assertThat(candidatesOf(requestId))
            .allSatisfy(candidate -> {
                assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.PENDING);
                assertThat(candidate.getRespondedAt()).isNull();
            });

        assertThat(findShift().getMember().getId()).isEqualTo(requester.getId());
    }

    private static void assertBusinessError(ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(expected);
    }

    // 퇴사 API가 없으므로 leftAt을 직접 채운다
    private void leave(WorkplaceMember member) {
        // 컨텍스트에 Lazy 프록시가 남아 있으면 findById가 프록시를 돌려주고,
        // 프록시에 setField 하면 실제 엔티티가 바뀌지 않으므로 먼저 비운다
        flushAndClear();

        WorkplaceMember managed = workplaceMemberRepository.findById(member.getId()).orElseThrow();
        ReflectionTestUtils.setField(managed, "leftAt", now.minusHours(1));
        flushAndClear();
    }

    private List<SubstituteCandidate> candidatesOf(Long requestId) {
        return substituteCandidateRepository.findAll().stream()
            .filter(candidate -> candidate.getRequest().getId().equals(requestId))
            .toList();
    }

    private SubstituteCandidate findCandidate(Long requestId, WorkplaceMember member) {
        return candidatesOf(requestId).stream()
            .filter(candidate -> candidate.getMember().getId().equals(member.getId()))
            .findFirst()
            .orElseThrow();
    }

    private Long candidateIdOf(Long requestId, WorkplaceMember member) {
        return findCandidate(requestId, member).getId();
    }

    private SubstituteRequest findRequest(Long requestId) {
        return substituteRequestRepository.findById(requestId).orElseThrow();
    }

    private Shift findShift() {
        return shiftRepository.findById(shift.getId()).orElseThrow();
    }

    private Long userId(WorkplaceMember member) {
        return member.getUser().getId();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private WorkplaceMember saveMember(String name, WorkplaceRole role) {
        User user = userRepository.save(
            new User("flow-" + name + "@test.com", "password-hash", name)
        );

        return workplaceMemberRepository.save(
            new WorkplaceMember(workplace, user, role, now.minusDays(30))
        );
    }

    private Shift saveShift(WorkplaceMember member, LocalDateTime startAt, int hours) {
        LocalDateTime start = startAt.withMinute(0).withSecond(0).withNano(0);

        return shiftRepository.save(
            new Shift(schedule, member, start, start.plusHours(hours), ShiftStatus.SCHEDULED)
        );
    }
}
