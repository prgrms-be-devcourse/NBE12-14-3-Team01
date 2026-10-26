package com.merge.backend.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.merge.backend.domain.notification.entity.Notification;
import com.merge.backend.domain.notification.entity.NotificationType;
import com.merge.backend.domain.shift.entity.Schedule;
import com.merge.backend.domain.shift.entity.Shift;
import com.merge.backend.domain.shift.entity.ShiftStatus;
import com.merge.backend.domain.shift.repository.ScheduleRepository;
import com.merge.backend.domain.shift.repository.ShiftRepository;
import com.merge.backend.domain.substitute.dto.SubstituteRequestCreateResponse;
import com.merge.backend.domain.substitute.entity.RequestStatus;
import com.merge.backend.domain.substitute.entity.SubstituteRequest;
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

    @Autowired
    private SubstituteRequestService substituteRequestService;

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

    // ===================== Fixture (Commit) =====================

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
}
