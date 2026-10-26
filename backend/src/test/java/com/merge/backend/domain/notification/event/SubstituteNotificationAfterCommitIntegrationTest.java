package com.merge.backend.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.merge.backend.domain.notification.entity.Notification;
import com.merge.backend.domain.notification.entity.NotificationType;
import com.merge.backend.domain.shift.entity.Schedule;
import com.merge.backend.domain.shift.entity.Shift;
import com.merge.backend.domain.shift.entity.ShiftStatus;
import com.merge.backend.domain.shift.repository.ScheduleRepository;
import com.merge.backend.domain.shift.repository.ShiftRepository;
import com.merge.backend.domain.substitute.dto.SubstituteRequestCreateResponse;
import com.merge.backend.domain.substitute.entity.CandidateStatus;
import com.merge.backend.domain.substitute.entity.RequestCloseReason;
import com.merge.backend.domain.substitute.entity.RequestStatus;
import com.merge.backend.domain.substitute.entity.SubstituteCandidate;
import com.merge.backend.domain.substitute.entity.SubstituteRequest;
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository;
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository;
import com.merge.backend.domain.substitute.service.SubstituteCandidateService;
import com.merge.backend.domain.substitute.service.SubstituteRequestService;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.user.repository.UserRepository;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.domain.workplace.repository.WorkplaceRepository;
import com.merge.backend.global.config.TestClock;
import com.merge.backend.global.config.TestTimeConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대타 Event의 Commit 후 알림 생성을 실제 MySQL Row로 확인한다 (V5-J3, 현재 Java 동작 고정).
 *
 * <p>Class에 {@code @Transactional}을 두지 않는다. 업무 Service의 Transaction이 실제로
 * Commit되어야 AFTER_COMMIT Listener가 실행되기 때문이다.
 *
 * <p>데이터는 지우지 않는다 (DOMAIN-DEVELOPMENT-GUIDE §3-2). 대신 Test마다 고유 접미사로
 * 데이터를 만들고, 알림은 그 Test가 만든 소속 ID로만 조회한다. 이 방식은 Container 재사용
 * 없음, 순차 실행, {@code ddl-auto: create}를 전제로 한다. 하나라도 바뀌면 다시 검토한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig.class)
class SubstituteNotificationAfterCommitIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-15T01:00:00Z");

    private static final String REQUEST_RECEIVED_MESSAGE = "새로운 대타 요청이 도착했습니다.";

    private static final String NO_CANDIDATE_MESSAGE = "대체 근무 가능한 후보가 없습니다.";

    private static final String ACCEPTED_MESSAGE =
        "대체 근무 수락자가 생겼습니다. 최종 승인이 필요합니다.";

    private static final String ALL_REJECTED_MESSAGE =
        "대체 근무 요청을 받은 모든 수신자가 거절했습니다.";

    private static final String APPROVED_REQUESTER_MESSAGE =
        "요청한 대체 근무 변경이 최종 승인되었습니다.";

    private static final String APPROVED_ACCEPTED_MESSAGE =
        "수락한 대체 근무가 공식 근무로 확정되었습니다.";

    private static final String MANAGER_CLOSED_MESSAGE = "관리자가 대체 근무 요청을 종료했습니다.";

    @Autowired
    private SubstituteRequestService substituteRequestService;

    @Autowired
    private SubstituteCandidateService substituteCandidateService;

    @Autowired
    private SubstituteRequestRepository substituteRequestRepository;

    @Autowired
    private SubstituteCandidateRepository substituteCandidateRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkplaceRepository workplaceRepository;

    @Autowired
    private WorkplaceMemberRepository workplaceMemberRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TestClock testClock;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void fixClock() {
        // 같은 Context를 쓰는 다른 Test가 시간을 바꿨을 수 있으므로 매번 고정
        testClock.setInstant(FIXED_NOW);
    }

    @Test
    @DisplayName("대타 요청이 Commit되면 후보에게만 요청 도착 알림이 1건씩 저장된다")
    void createRequest_savesReceivedNotificationOnlyForCandidatesAfterCommit() {
        // given
        RequestFixture fixture = createRequestFixture();

        // 다른 Test가 남긴 데이터와 섞이지 않았는지 먼저 확인한다
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty();

        // when - 바깥 Transaction 없이 호출해 Service의 Transaction이 실제로 Commit된다
        SubstituteRequestCreateResponse response =
            substituteRequestService.create(fixture.shiftId(), fixture.requesterUserId());

        // then - 응답
        assertThat(response.requestCreated()).isTrue();
        assertThat(response.candidateCount()).isEqualTo(2);

        // then - 업무 DB: OPEN Request 1건과 후보 C1·C2
        List<SubstituteRequest> requests = requestsOf(fixture.shiftId());
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(candidateMemberIdsOf(requests.get(0).getId()))
            .containsExactlyInAnyOrder(
                fixture.firstCandidateMemberId(),
                fixture.secondCandidateMemberId()
            );

        // then - 알림 DB: 받는 사람과 받지 않아야 할 사람을 함께 조회한다
        List<Notification> notifications = notificationsOf(fixture.allMemberIds());

        assertThat(notifications)
            .extracting(notification -> notification.getRecipientMember().getId())
            .containsExactlyInAnyOrder(
                fixture.firstCandidateMemberId(),
                fixture.secondCandidateMemberId()
            );
        assertThat(notifications)
            .allSatisfy(notification -> {
                assertThat(notification.getType())
                    .isEqualTo(NotificationType.SUBSTITUTE_REQUEST_RECEIVED);
                assertThat(notification.getMessage()).isEqualTo(REQUEST_RECEIVED_MESSAGE);
                assertThat(notification.getReadAt()).isNull();
            });
    }

    @Test
    @DisplayName("후보가 없으면 Request 없이 Commit되고 현재 MANAGER에게만 후보 없음 알림이 1건 저장된다")
    void createRequestWithoutCandidate_savesNoCandidateNotificationOnlyForActiveManagers() {
        // given - 요청자 외에 직원이 없다 (MANAGER는 후보 대상이 아니다)
        NoCandidateFixture fixture = createNoCandidateFixture();
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty();

        // when - 현재 Java는 예외 없이 requestCreated=false를 돌려준다
        SubstituteRequestCreateResponse response =
            substituteRequestService.create(fixture.shiftId(), fixture.requesterUserId());

        // then - 응답
        assertThat(response.requestCreated()).isFalse();
        assertThat(response.requestId()).isNull();
        assertThat(response.candidateCount()).isZero();

        // then - 업무 DB: Request를 저장하지 않는다
        assertThat(requestsOf(fixture.shiftId())).isEmpty();

        // then - 알림 DB: 업무 쓰기가 없어도 Commit 후 Listener가 실행된다
        assertNotifiedOnly(
            notificationsOf(fixture.allMemberIds()),
            List.of(fixture.activeManagerMemberId()),
            NotificationType.SUBSTITUTE_NO_CANDIDATE,
            NO_CANDIDATE_MESSAGE
        );
    }

    @Test
    @DisplayName("후보가 수락하면 Commit 후 현재 MANAGER 전원에게만 수락 알림이 1건씩 저장된다")
    void acceptCandidate_savesAcceptedNotificationOnlyForActiveManagersAfterCommit() {
        // given - Service가 만드는 것과 같은 OPEN Request와 PENDING 후보 2명 (알림 없이 Commit)
        OpenRequestFixture fixture = createOpenRequestFixture();
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty();

        // when - 후보 C1이 실제 Service로 수락한다
        substituteCandidateService.acceptCandidate(
            fixture.firstCandidateId(),
            fixture.firstCandidateUserId()
        );

        // then - 업무 DB: Request·C1 ACCEPTED, C2는 PENDING 그대로
        assertThat(requestStatusOf(fixture.requestId())).isEqualTo(RequestStatus.ACCEPTED);
        assertThat(candidateStatusOf(fixture.firstCandidateId()))
            .isEqualTo(CandidateStatus.ACCEPTED);
        assertThat(candidateStatusOf(fixture.secondCandidateId()))
            .isEqualTo(CandidateStatus.PENDING);

        // then - 알림 DB: 수락한 후보·요청자·다른 후보·퇴사한 MANAGER는 받지 않는다
        assertNotifiedOnly(
            notificationsOf(fixture.allMemberIds()),
            List.of(fixture.firstManagerMemberId(), fixture.secondManagerMemberId()),
            NotificationType.SUBSTITUTE_ACCEPTED,
            ACCEPTED_MESSAGE
        );
    }

    @Test
    @DisplayName("마지막 후보가 거절하면 Request가 종료되고 요청자와 현재 MANAGER에게만 전원 거절 알림이 저장된다")
    void rejectLastCandidate_savesAllRejectedNotificationForRequesterAndActiveManagers() {
        // given - PENDING 후보가 C1 한 명뿐인 OPEN Request (알림 없이 Commit)
        SingleCandidateRequestFixture fixture = createSingleCandidateRequestFixture();
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty();

        // when - 유일한 후보 C1이 실제 Service로 거절한다
        substituteCandidateService.rejectCandidate(
            fixture.candidateId(),
            fixture.candidateUserId()
        );

        // then - 업무 DB: Request CLOSED (전원 거절), C1 REJECTED
        SubstituteRequest request = requestOf(fixture.requestId());
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CLOSED);
        assertThat(request.getCloseReason())
            .isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED);
        assertThat(candidateStatusOf(fixture.candidateId()))
            .isEqualTo(CandidateStatus.REJECTED);

        // then - 알림 DB: 거절한 C1과 퇴사한 MANAGER는 받지 않는다
        assertNotifiedOnly(
            notificationsOf(fixture.allMemberIds()),
            List.of(fixture.requesterMemberId(), fixture.activeManagerMemberId()),
            NotificationType.SUBSTITUTE_ALL_REJECTED,
            ALL_REJECTED_MESSAGE
        );
    }

    @Test
    @DisplayName("다른 후보가 아직 PENDING이면 거절해도 Request는 OPEN이고 관련 구성원에게 알림이 저장되지 않는다")
    void rejectNotLastCandidate_keepsRequestOpenAndSavesNoNotification() {
        // given - PENDING 후보 C1·C2가 있는 OPEN Request (알림 없이 Commit)
        OpenRequestFixture fixture = createOpenRequestFixture();
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty();

        // when - C1만 실제 Service로 거절한다
        substituteCandidateService.rejectCandidate(
            fixture.firstCandidateId(),
            fixture.firstCandidateUserId()
        );

        // then - 업무 DB: Request는 OPEN 그대로, C1 REJECTED, C2 PENDING
        SubstituteRequest request = requestOf(fixture.requestId());
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(request.getCloseReason()).isNull();
        assertThat(candidateStatusOf(fixture.firstCandidateId()))
            .isEqualTo(CandidateStatus.REJECTED);
        assertThat(candidateStatusOf(fixture.secondCandidateId()))
            .isEqualTo(CandidateStatus.PENDING);

        // then - 알림 DB: 이 Test가 만든 구성원 누구에게도 Row가 없다
        // (DB 결과 확인이며, Event가 발행되지 않았다는 것을 직접 확인하지는 않는다)
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty();
    }

    @Test
    @DisplayName("MANAGER가 승인하면 Shift 담당자가 수락자로 바뀌고 요청자와 수락자에게 각자의 승인 알림이 저장된다")
    void approveRequest_movesShiftAndSavesApprovedNotificationsForRequesterAndAccepted() {
        // given - C1이 수락한 ACCEPTED Request, C2는 PENDING (J3-03 결과와 같은 상태)
        ManagerActionFixture fixture = createManagerActionFixture(true, false);
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty();

        // when - MANAGER가 실제 Service로 승인한다
        substituteRequestService.approve(fixture.requestId(), fixture.managerUserId());

        // then - 업무 DB: Request APPROVED, Shift 담당자가 C1으로 바뀐다
        assertThat(requestOf(fixture.requestId()).getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(shiftMemberIdOf(fixture.shiftId())).isEqualTo(fixture.firstCandidateMemberId());

        // then - 알림 DB: 같은 Type이지만 받는 사람마다 문구가 다르다. MANAGER·C2는 받지 않는다
        List<Notification> notifications = notificationsOf(fixture.allMemberIds());
        assertThat(notifications)
            .extracting(
                notification -> notification.getRecipientMember().getId(),
                Notification::getType,
                Notification::getMessage,
                Notification::getReadAt
            )
            .containsExactlyInAnyOrder(
                tuple(
                    fixture.requesterMemberId(),
                    NotificationType.SUBSTITUTE_APPROVED,
                    APPROVED_REQUESTER_MESSAGE,
                    null
                ),
                tuple(
                    fixture.firstCandidateMemberId(),
                    NotificationType.SUBSTITUTE_APPROVED,
                    APPROVED_ACCEPTED_MESSAGE,
                    null
                )
            );
    }

    @Test
    @DisplayName("OPEN Request를 MANAGER가 종료하면 요청자와 PENDING 후보에게만 종료 알림이 저장되고 담당자는 그대로다")
    void closeOpenRequest_savesClosedNotificationForRequesterAndPendingCandidates() {
        // given - C2가 거절했고 C1은 PENDING인 OPEN Request (J3-04b 결과와 같은 상태)
        ManagerActionFixture fixture = createManagerActionFixture(false, true);
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty();

        // when - MANAGER가 실제 Service로 종료한다
        substituteRequestService.close(fixture.requestId(), fixture.managerUserId());

        // then - 업무 DB: CLOSED (MANAGER_CLOSED), Shift 담당자는 요청자 그대로
        SubstituteRequest request = requestOf(fixture.requestId());
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CLOSED);
        assertThat(request.getCloseReason()).isEqualTo(RequestCloseReason.MANAGER_CLOSED);
        assertThat(shiftMemberIdOf(fixture.shiftId())).isEqualTo(fixture.requesterMemberId());

        // then - 알림 DB: OPEN 종료는 요청자 + PENDING 후보. 이미 거절한 C2와 MANAGER는 받지 않는다
        assertNotifiedOnly(
            notificationsOf(fixture.allMemberIds()),
            List.of(fixture.requesterMemberId(), fixture.firstCandidateMemberId()),
            NotificationType.SUBSTITUTE_MANAGER_CLOSED,
            MANAGER_CLOSED_MESSAGE
        );
    }

    @Test
    @DisplayName("ACCEPTED Request를 MANAGER가 종료하면 요청자와 수락자에게만 종료 알림이 저장되고 담당자는 그대로다")
    void closeAcceptedRequest_savesClosedNotificationForRequesterAndAccepted() {
        // given - C1이 수락한 ACCEPTED Request, C2는 PENDING (J3-03 결과와 같은 상태)
        ManagerActionFixture fixture = createManagerActionFixture(true, false);
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty();

        // when - MANAGER가 실제 Service로 종료한다
        substituteRequestService.close(fixture.requestId(), fixture.managerUserId());

        // then - 업무 DB: CLOSED (MANAGER_CLOSED), Shift 담당자는 요청자 그대로
        SubstituteRequest request = requestOf(fixture.requestId());
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CLOSED);
        assertThat(request.getCloseReason()).isEqualTo(RequestCloseReason.MANAGER_CLOSED);
        assertThat(shiftMemberIdOf(fixture.shiftId())).isEqualTo(fixture.requesterMemberId());

        // then - 알림 DB: ACCEPTED 종료는 요청자 + 수락자. PENDING으로 남은 C2와 MANAGER는 받지 않는다
        assertNotifiedOnly(
            notificationsOf(fixture.allMemberIds()),
            List.of(fixture.requesterMemberId(), fixture.firstCandidateMemberId()),
            NotificationType.SUBSTITUTE_MANAGER_CLOSED,
            MANAGER_CLOSED_MESSAGE
        );
    }

    // 받는 사람마다 정확히 1건이고, 나머지 조회 대상은 0건이다
    private void assertNotifiedOnly(
        List<Notification> notifications,
        List<Long> expectedRecipientMemberIds,
        NotificationType expectedType,
        String expectedMessage
    ) {
        assertThat(notifications)
            .extracting(notification -> notification.getRecipientMember().getId())
            .containsExactlyInAnyOrderElementsOf(expectedRecipientMemberIds);
        assertThat(notifications)
            .allSatisfy(notification -> {
                assertThat(notification.getType()).isEqualTo(expectedType);
                assertThat(notification.getMessage()).isEqualTo(expectedMessage);
                assertThat(notification.getReadAt()).isNull();
            });
    }

    // ===================== 조회 (새 Transaction) =====================

    // 목록 Query(findAllByUserId)는 현재 소속만 돌려주므로 생성 검증에 쓰지 않는다
    private List<Notification> notificationsOf(List<Long> memberIds) {
        return new TransactionTemplate(transactionManager).execute(status ->
            entityManager.createQuery(
                    "select n from Notification n join fetch n.recipientMember rm"
                        + " where rm.id in :memberIds",
                    Notification.class
                )
                .setParameter("memberIds", memberIds)
                .getResultList()
        );
    }

    private List<SubstituteRequest> requestsOf(Long shiftId) {
        return new TransactionTemplate(transactionManager).execute(status ->
            entityManager.createQuery(
                    "select r from SubstituteRequest r where r.shift.id = :shiftId",
                    SubstituteRequest.class
                )
                .setParameter("shiftId", shiftId)
                .getResultList()
        );
    }

    private List<Long> candidateMemberIdsOf(Long requestId) {
        return new TransactionTemplate(transactionManager).execute(status ->
            entityManager.createQuery(
                    "select c.member.id from SubstituteCandidate c"
                        + " where c.request.id = :requestId",
                    Long.class
                )
                .setParameter("requestId", requestId)
                .getResultList()
        );
    }

    private RequestStatus requestStatusOf(Long requestId) {
        return new TransactionTemplate(transactionManager).execute(status ->
            entityManager.find(SubstituteRequest.class, requestId).getStatus()
        );
    }

    private SubstituteRequest requestOf(Long requestId) {
        return new TransactionTemplate(transactionManager).execute(status ->
            entityManager.find(SubstituteRequest.class, requestId)
        );
    }

    private Long shiftMemberIdOf(Long shiftId) {
        return new TransactionTemplate(transactionManager).execute(status ->
            entityManager.find(Shift.class, shiftId).getMember().getId()
        );
    }

    private CandidateStatus candidateStatusOf(Long candidateId) {
        return new TransactionTemplate(transactionManager).execute(status ->
            entityManager.find(SubstituteCandidate.class, candidateId).getStatus()
        );
    }

    // ===================== Fixture (Commit) =====================

    // 현재 MANAGER 1, 퇴사한 MANAGER 1, 요청자 1 + 요청자의 미래 Shift. 다른 직원은 없다
    private NoCandidateFixture createNoCandidateFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = uniqueSuffix();
            Workplace workplace = saveWorkplace(suffix);
            Schedule schedule = savePublishedSchedule(workplace);

            WorkplaceMember activeManager = saveMember(
                workplace, "manager-" + suffix, WorkplaceRole.MANAGER, null
            );
            WorkplaceMember leftManager = saveMember(
                workplace, "left-manager-" + suffix, WorkplaceRole.MANAGER, now().minusDays(1)
            );
            WorkplaceMember requester = saveMember(
                workplace, "requester-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            Shift shift = saveShift(schedule, requester);

            return new NoCandidateFixture(
                shift.getId(),
                requester.getUser().getId(),
                requester.getId(),
                activeManager.getId(),
                leftManager.getId()
            );
        });
    }

    // MANAGER 2 + 퇴사한 MANAGER 1, 요청자, OPEN Request와 PENDING 후보 C1·C2
    // create()가 저장하는 것과 같은 상태를 Repository로 만든다 (Service를 거치지 않아 알림이 없다)
    private OpenRequestFixture createOpenRequestFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = uniqueSuffix();
            Workplace workplace = saveWorkplace(suffix);
            Schedule schedule = savePublishedSchedule(workplace);

            WorkplaceMember firstManager = saveMember(
                workplace, "manager1-" + suffix, WorkplaceRole.MANAGER, null
            );
            WorkplaceMember secondManager = saveMember(
                workplace, "manager2-" + suffix, WorkplaceRole.MANAGER, null
            );
            WorkplaceMember leftManager = saveMember(
                workplace, "left-manager-" + suffix, WorkplaceRole.MANAGER, now().minusDays(1)
            );
            WorkplaceMember requester = saveMember(
                workplace, "requester-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            WorkplaceMember firstCandidateMember = saveMember(
                workplace, "candidate1-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            WorkplaceMember secondCandidateMember = saveMember(
                workplace, "candidate2-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            Shift shift = saveShift(schedule, requester);

            SubstituteRequest request = substituteRequestRepository.save(
                new SubstituteRequest(shift, requester, RequestStatus.OPEN)
            );
            SubstituteCandidate firstCandidate = substituteCandidateRepository.save(
                new SubstituteCandidate(request, firstCandidateMember)
            );
            SubstituteCandidate secondCandidate = substituteCandidateRepository.save(
                new SubstituteCandidate(request, secondCandidateMember)
            );

            return new OpenRequestFixture(
                request.getId(),
                firstCandidate.getId(),
                secondCandidate.getId(),
                firstCandidateMember.getUser().getId(),
                requester.getId(),
                firstManager.getId(),
                secondManager.getId(),
                leftManager.getId(),
                firstCandidateMember.getId(),
                secondCandidateMember.getId()
            );
        });
    }

    // 현재 MANAGER 1, 퇴사한 MANAGER 1, 요청자, OPEN Request와 유일한 PENDING 후보 C1
    private SingleCandidateRequestFixture createSingleCandidateRequestFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = uniqueSuffix();
            Workplace workplace = saveWorkplace(suffix);
            Schedule schedule = savePublishedSchedule(workplace);

            WorkplaceMember activeManager = saveMember(
                workplace, "manager-" + suffix, WorkplaceRole.MANAGER, null
            );
            WorkplaceMember leftManager = saveMember(
                workplace, "left-manager-" + suffix, WorkplaceRole.MANAGER, now().minusDays(1)
            );
            WorkplaceMember requester = saveMember(
                workplace, "requester-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            WorkplaceMember candidateMember = saveMember(
                workplace, "candidate-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            Shift shift = saveShift(schedule, requester);

            SubstituteRequest request = substituteRequestRepository.save(
                new SubstituteRequest(shift, requester, RequestStatus.OPEN)
            );
            SubstituteCandidate candidate = substituteCandidateRepository.save(
                new SubstituteCandidate(request, candidateMember)
            );

            return new SingleCandidateRequestFixture(
                request.getId(),
                candidate.getId(),
                candidateMember.getUser().getId(),
                requester.getId(),
                activeManager.getId(),
                leftManager.getId(),
                candidateMember.getId()
            );
        });
    }

    // MANAGER, 요청자, 후보 C1·C2의 OPEN Request에서 MANAGER 처리 직전 상태를 만든다
    // - acceptFirst: C1 수락 → Request·C1 ACCEPTED, C2 PENDING (J3-03 결과와 같은 상태)
    // - rejectSecond: C2 거절 → Request OPEN, C1 PENDING (J3-04b 결과와 같은 상태)
    // Service가 검증 뒤 호출하는 Entity 메서드를 그대로 써서 알림 없이 Commit한다
    private ManagerActionFixture createManagerActionFixture(
        boolean acceptFirst,
        boolean rejectSecond
    ) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = uniqueSuffix();
            Workplace workplace = saveWorkplace(suffix);
            Schedule schedule = savePublishedSchedule(workplace);

            WorkplaceMember manager = saveMember(
                workplace, "manager-" + suffix, WorkplaceRole.MANAGER, null
            );
            WorkplaceMember requester = saveMember(
                workplace, "requester-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            WorkplaceMember firstCandidateMember = saveMember(
                workplace, "candidate1-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            WorkplaceMember secondCandidateMember = saveMember(
                workplace, "candidate2-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            Shift shift = saveShift(schedule, requester);

            SubstituteRequest request = substituteRequestRepository.save(
                new SubstituteRequest(shift, requester, RequestStatus.OPEN)
            );
            SubstituteCandidate firstCandidate = substituteCandidateRepository.save(
                new SubstituteCandidate(request, firstCandidateMember)
            );
            SubstituteCandidate secondCandidate = substituteCandidateRepository.save(
                new SubstituteCandidate(request, secondCandidateMember)
            );
            if (acceptFirst) {
                firstCandidate.acceptRequest(now());
            }
            if (rejectSecond) {
                secondCandidate.reject(now());
            }

            return new ManagerActionFixture(
                request.getId(),
                shift.getId(),
                manager.getUser().getId(),
                manager.getId(),
                requester.getId(),
                firstCandidateMember.getId(),
                secondCandidateMember.getId()
            );
        });
    }

    private Workplace saveWorkplace(String suffix) {
        return workplaceRepository.save(
            new Workplace("대타 알림 테스트 매장 " + suffix, "SUBN-" + suffix)
        );
    }

    // 일주일 뒤 10:00~14:00
    private Shift saveShift(Schedule schedule, WorkplaceMember member) {
        LocalDateTime startAt = shiftStartAt();
        return shiftRepository.save(
            new Shift(schedule, member, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED)
        );
    }

    // MANAGER 1, 요청자 1, 후보 2, 퇴사한 직원 1 + 요청자의 미래 Shift (공개된 근무표)
    private RequestFixture createRequestFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = uniqueSuffix();
            Workplace workplace = workplaceRepository.save(
                new Workplace("대타 알림 테스트 매장 " + suffix, "SUBN-" + suffix)
            );
            Schedule schedule = savePublishedSchedule(workplace);

            WorkplaceMember manager = saveMember(
                workplace, "manager-" + suffix, WorkplaceRole.MANAGER, null
            );
            WorkplaceMember requester = saveMember(
                workplace, "requester-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            WorkplaceMember firstCandidate = saveMember(
                workplace, "candidate1-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            WorkplaceMember secondCandidate = saveMember(
                workplace, "candidate2-" + suffix, WorkplaceRole.EMPLOYEE, null
            );
            WorkplaceMember leftEmployee = saveMember(
                workplace, "left-" + suffix, WorkplaceRole.EMPLOYEE, now().minusDays(1)
            );

            LocalDateTime startAt = shiftStartAt();
            Shift shift = shiftRepository.save(
                new Shift(schedule, requester, startAt, startAt.plusHours(4),
                    ShiftStatus.SCHEDULED)
            );

            return new RequestFixture(
                shift.getId(),
                requester.getUser().getId(),
                requester.getId(),
                manager.getId(),
                firstCandidate.getId(),
                secondCandidate.getId(),
                leftEmployee.getId()
            );
        });
    }

    private Schedule savePublishedSchedule(Workplace workplace) {
        Schedule schedule = new Schedule(
            workplace,
            shiftStartAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        );
        schedule.publish(now());
        return scheduleRepository.save(schedule);
    }

    private WorkplaceMember saveMember(
        Workplace workplace,
        String name,
        WorkplaceRole role,
        LocalDateTime leftAt
    ) {
        User user = userRepository.save(new User(name + "@test.com", "password", name));
        return workplaceMemberRepository.save(
            new WorkplaceMember(workplace, user, role, now().minusDays(30), leftAt)
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(testClock);
    }

    // 일주일 뒤 10:00
    private LocalDateTime shiftStartAt() {
        return now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record RequestFixture(
        Long shiftId,
        Long requesterUserId,
        Long requesterMemberId,
        Long managerMemberId,
        Long firstCandidateMemberId,
        Long secondCandidateMemberId,
        Long leftEmployeeMemberId
    ) {

        // 받는 사람(후보)과 받지 않아야 할 사람(요청자·MANAGER·퇴사자)을 모두 포함한다
        List<Long> allMemberIds() {
            return List.of(
                requesterMemberId,
                managerMemberId,
                firstCandidateMemberId,
                secondCandidateMemberId,
                leftEmployeeMemberId
            );
        }
    }

    private record NoCandidateFixture(
        Long shiftId,
        Long requesterUserId,
        Long requesterMemberId,
        Long activeManagerMemberId,
        Long leftManagerMemberId
    ) {

        List<Long> allMemberIds() {
            return List.of(requesterMemberId, activeManagerMemberId, leftManagerMemberId);
        }
    }

    private record ManagerActionFixture(
        Long requestId,
        Long shiftId,
        Long managerUserId,
        Long managerMemberId,
        Long requesterMemberId,
        Long firstCandidateMemberId,
        Long secondCandidateMemberId
    ) {

        List<Long> allMemberIds() {
            return List.of(
                managerMemberId,
                requesterMemberId,
                firstCandidateMemberId,
                secondCandidateMemberId
            );
        }
    }

    private record SingleCandidateRequestFixture(
        Long requestId,
        Long candidateId,
        Long candidateUserId,
        Long requesterMemberId,
        Long activeManagerMemberId,
        Long leftManagerMemberId,
        Long candidateMemberId
    ) {

        List<Long> allMemberIds() {
            return List.of(
                requesterMemberId,
                activeManagerMemberId,
                leftManagerMemberId,
                candidateMemberId
            );
        }
    }

    private record OpenRequestFixture(
        Long requestId,
        Long firstCandidateId,
        Long secondCandidateId,
        Long firstCandidateUserId,
        Long requesterMemberId,
        Long firstManagerMemberId,
        Long secondManagerMemberId,
        Long leftManagerMemberId,
        Long firstCandidateMemberId,
        Long secondCandidateMemberId
    ) {

        List<Long> allMemberIds() {
            return List.of(
                requesterMemberId,
                firstManagerMemberId,
                secondManagerMemberId,
                leftManagerMemberId,
                firstCandidateMemberId,
                secondCandidateMemberId
            );
        }
    }
}
