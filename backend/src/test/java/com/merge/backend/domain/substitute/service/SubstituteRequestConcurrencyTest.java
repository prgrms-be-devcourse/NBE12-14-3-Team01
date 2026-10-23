package com.merge.backend.domain.substitute.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.merge.backend.domain.shift.entity.Schedule;
import com.merge.backend.domain.shift.entity.Shift;
import com.merge.backend.domain.shift.entity.ShiftStatus;
import com.merge.backend.domain.shift.repository.ScheduleRepository;
import com.merge.backend.domain.shift.repository.ShiftRepository;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig.class)
class SubstituteRequestConcurrencyTest {

    private static final int THREAD_COUNT = 10;
    private static final int REPEAT_COUNT = 5;

    // second 작업이 이 시간 안에 끝나지 않으면 "Lock 대기 중"으로 판단
    private static final long LOCK_WAIT_CHECK_MILLIS = 1_000;

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
    private UserRepository userRepository;

    @Autowired
    private WorkplaceRepository workplaceRepository;

    @Autowired
    private WorkplaceMemberRepository workplaceMemberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TestClock testClock;

    @BeforeEach
    void fixClock() {
        // 같은 Context를 쓰는 다른 테스트가 시간을 바꿨을 수 있으므로 매번 고정
        testClock.setInstant(FIXED_NOW);
    }

    // =====================================================================
    // 1. 스레드 10개가 동시에 실행 (반복 실행으로 우연히 통과하는 경우를 줄인다)
    // =====================================================================

    @Nested
    @DisplayName("스레드 10개 동시 실행")
    class SimultaneousTest {

        @RepeatedTest(REPEAT_COUNT)
        @DisplayName("같은 Shift에 대타 요청을 동시에 생성해도 활성 요청은 하나만 생성된다")
        void concurrentCreateAllowsOnlyOneActiveRequest() throws Exception {

            // given
            ShiftFixture fixture = createShiftFixture();

            // when
            List<Throwable> results = runSimultaneously(
                IntStream.range(0, THREAD_COUNT)
                    .<Runnable>mapToObj(i -> () -> substituteRequestService.create(
                        fixture.shiftId(),
                        fixture.requesterUserId()
                    ))
                    .toList()
            );

            // then
            assertOneSucceedsOthersFailWith(
                results,
                SubstituteRequestErrorCode.ACTIVE_REQUEST_EXISTS
            );

            assertThat(countActiveRequests(fixture)).isEqualTo(1);
        }

        @RepeatedTest(REPEAT_COUNT)
        @DisplayName("같은 Request의 Candidate 10명이 동시에 수락해도 한 명만 ACCEPTED 된다")
        void concurrentAcceptAllowsOnlyOneCandidate() throws Exception {

            // given
            RequestFixture fixture = createRequestFixture(THREAD_COUNT);

            // when
            List<Throwable> results = runSimultaneously(
                IntStream.range(0, THREAD_COUNT)
                    .<Runnable>mapToObj(i -> () -> substituteCandidateService.acceptCandidate(
                        fixture.candidateIds().get(i),
                        fixture.candidateUserIds().get(i)
                    ))
                    .toList()
            );

            // then
            assertOneSucceedsOthersFailWith(
                results,
                SubstituteRequestErrorCode.REQUEST_NOT_OPEN
            );

            inTransaction(() -> {
                assertThat(findRequest(fixture.requestId()).getStatus())
                    .isEqualTo(RequestStatus.ACCEPTED);

                assertThat(candidateStatuses(fixture.candidateIds()))
                    .containsOnlyOnce(CandidateStatus.ACCEPTED)
                    .filteredOn(status -> status != CandidateStatus.ACCEPTED)
                    .containsOnly(CandidateStatus.PENDING);
            });
        }

        @RepeatedTest(REPEAT_COUNT)
        @DisplayName("Candidate 10명이 동시에 모두 거절하면 Request는 반드시 CLOSED 된다")
        void concurrentRejectClosesRequestWhenNoPendingCandidateRemains() throws Exception {

            // given
            RequestFixture fixture = createRequestFixture(THREAD_COUNT);

            // when
            List<Throwable> results = runSimultaneously(
                IntStream.range(0, THREAD_COUNT)
                    .<Runnable>mapToObj(i -> () -> substituteCandidateService.rejectCandidate(
                        fixture.candidateIds().get(i),
                        fixture.candidateUserIds().get(i)
                    ))
                    .toList()
            );

            // then - 모두 성공
            assertThat(results).containsOnlyNulls();

            // then - Lock이 없으면 "서로 상대가 아직 PENDING"이라고 보고 Request가 OPEN으로 남는다
            inTransaction(() -> {
                SubstituteRequest request = findRequest(fixture.requestId());

                assertThat(request.getStatus()).isEqualTo(RequestStatus.CLOSED);
                assertThat(request.getCloseReason())
                    .isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED);

                assertThat(candidateStatuses(fixture.candidateIds()))
                    .containsOnly(CandidateStatus.REJECTED);
            });
        }

        @RepeatedTest(REPEAT_COUNT)
        @DisplayName("같은 User가 시간이 겹치는 서로 다른 Request 10개를 동시에 수락해도 하나만 ACCEPTED 된다")
        void concurrentAcceptOnDifferentRequestsAllowsOnlyOneForSameUser() throws Exception {

            // given
            SameUserFixture fixture = createSameUserFixture(THREAD_COUNT);

            // when
            List<Throwable> results = runSimultaneously(
                IntStream.range(0, THREAD_COUNT)
                    .<Runnable>mapToObj(i -> () -> substituteCandidateService.acceptCandidate(
                        fixture.candidateIds().get(i),
                        fixture.candidateUserId()
                    ))
                    .toList()
            );

            // then
            assertOneSucceedsOthersFailWith(
                results,
                SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE
            );

            inTransaction(() -> {
                assertThat(fixture.requestIds())
                    .extracting(requestId -> findRequest(requestId).getStatus())
                    .containsOnlyOnce(RequestStatus.ACCEPTED)
                    .filteredOn(status -> status != RequestStatus.ACCEPTED)
                    .containsOnly(RequestStatus.OPEN);

                assertThat(candidateStatuses(fixture.candidateIds()))
                    .containsOnlyOnce(CandidateStatus.ACCEPTED);
            });
        }
    }

    // =====================================================================
    // 2. 실행 순서를 강제한 테스트
    //    first가 Lock을 쥔 채 커밋 전에 멈춘 상태에서 second를 실행한다.
    //    → second가 실제로 기다리는지 + first 커밋 후 최신 상태를 보고 판단하는지 확인
    // =====================================================================

    @Nested
    @DisplayName("실행 순서 강제 (first 커밋 전 second 실행)")
    class ForcedOrderTest {

        @Test
        @DisplayName("Shift Lock - 먼저 생성 중인 요청이 커밋될 때까지 두 번째 생성은 대기하고, "
            + "이후 ACTIVE_REQUEST_EXISTS로 실패한다")
        void secondCreateWaitsForFirstCreate() throws Exception {

            // given
            ShiftFixture fixture = createShiftFixture();

            // when
            Throwable secondError = runSecondWhileFirstHoldsLock(
                () -> substituteRequestService.create(
                    fixture.shiftId(), fixture.requesterUserId()
                ),
                () -> substituteRequestService.create(
                    fixture.shiftId(), fixture.requesterUserId()
                )
            );

            // then
            assertBusinessError(secondError, SubstituteRequestErrorCode.ACTIVE_REQUEST_EXISTS);
            assertThat(countActiveRequests(fixture)).isEqualTo(1);
        }

        @Test
        @DisplayName("Request Lock - 첫 번째 후보의 수락이 커밋될 때까지 두 번째 후보는 대기하고, "
            + "이후 REQUEST_NOT_OPEN으로 실패한다")
        void secondAcceptWaitsForFirstAccept() throws Exception {

            // given
            RequestFixture fixture = createRequestFixture(2);

            // when
            Throwable secondError = runSecondWhileFirstHoldsLock(
                () -> substituteCandidateService.acceptCandidate(
                    fixture.candidateIds().get(0), fixture.candidateUserIds().get(0)
                ),
                () -> substituteCandidateService.acceptCandidate(
                    fixture.candidateIds().get(1), fixture.candidateUserIds().get(1)
                )
            );

            // then
            assertBusinessError(secondError, SubstituteRequestErrorCode.REQUEST_NOT_OPEN);

            inTransaction(() -> {
                assertThat(findRequest(fixture.requestId()).getStatus())
                    .isEqualTo(RequestStatus.ACCEPTED);

                assertThat(candidateStatuses(fixture.candidateIds()))
                    .containsExactly(CandidateStatus.ACCEPTED, CandidateStatus.PENDING);
            });
        }

        @Test
        @DisplayName("Request Lock - 첫 번째 거절이 커밋된 뒤 두 번째(마지막) 거절이 실행되어 Request가 CLOSED 된다")
        void lastRejectSeesCommittedFirstReject() throws Exception {

            // given
            RequestFixture fixture = createRequestFixture(2);

            // when
            Throwable secondError = runSecondWhileFirstHoldsLock(
                () -> substituteCandidateService.rejectCandidate(
                    fixture.candidateIds().get(0), fixture.candidateUserIds().get(0)
                ),
                () -> substituteCandidateService.rejectCandidate(
                    fixture.candidateIds().get(1), fixture.candidateUserIds().get(1)
                )
            );

            // then
            assertThat(secondError).isNull();

            inTransaction(() -> {
                SubstituteRequest request = findRequest(fixture.requestId());

                assertThat(request.getStatus()).isEqualTo(RequestStatus.CLOSED);
                assertThat(request.getCloseReason())
                    .isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED);
            });
        }

        @Test
        @DisplayName("User Lock - 같은 User가 다른 Request를 수락하는 중이면 대기하고, "
            + "이후 CONFLICT_ACTIVE_SUBSTITUTE로 실패한다")
        void sameUserSecondAcceptWaitsForFirstAccept() throws Exception {

            // given - Request가 서로 다르므로 Request Lock으로는 막히지 않는다
            SameUserFixture fixture = createSameUserFixture(2);

            // when
            Throwable secondError = runSecondWhileFirstHoldsLock(
                () -> substituteCandidateService.acceptCandidate(
                    fixture.candidateIds().get(0), fixture.candidateUserId()
                ),
                () -> substituteCandidateService.acceptCandidate(
                    fixture.candidateIds().get(1), fixture.candidateUserId()
                )
            );

            // then
            assertBusinessError(secondError, SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE);

            inTransaction(() -> {
                assertThat(findRequest(fixture.requestIds().get(0)).getStatus())
                    .isEqualTo(RequestStatus.ACCEPTED);
                assertThat(findRequest(fixture.requestIds().get(1)).getStatus())
                    .isEqualTo(RequestStatus.OPEN);
            });
        }
    }

    // ===================== 실행 헬퍼 =====================

    // 모든 작업을 준비시킨 뒤 동시에 출발시키고, 각 작업의 예외(성공 시 null)를 모은다
    private List<Throwable> runSimultaneously(List<Runnable> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Throwable>> futures = new ArrayList<>();

            for (Runnable task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return capture(task);
                }));
            }

            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();

            List<Throwable> results = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                results.add(future.get(30, SECONDS));
            }
            return results;

        } finally {
            executor.shutdownNow();
        }
    }

    /*
     * 1) first를 트랜잭션 안에서 실행하고, 커밋하지 않은 채(Lock을 쥔 채) 멈춘다.
     * 2) second를 실행한다. Lock이 있다면 second는 first 커밋 전까지 끝날 수 없다.
     * 3) first를 커밋시키고 second의 결과(예외, 성공 시 null)를 반환한다.
     *
     * Lock이 없다면 second가 first 커밋 전에 끝나 버리므로 2)에서 실패하고,
     * 우연히 늦게 끝나더라도 커밋 안 된 데이터를 기준으로 판단하므로 결과 검증에서 실패한다.
     */
    private Throwable runSecondWhileFirstHoldsLock(
        Runnable first,
        Runnable second
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstExecuted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try {
            Future<?> firstFuture = executor.submit(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    first.run();
                    firstExecuted.countDown();
                    await(releaseFirst); // 커밋하지 않고 대기
                })
            );

            assertThat(firstExecuted.await(10, SECONDS))
                .as("first 작업이 커밋 전 상태로 대기해야 한다")
                .isTrue();

            Future<Throwable> secondFuture = executor.submit(() -> capture(second));

            assertThatThrownBy(() -> secondFuture.get(LOCK_WAIT_CHECK_MILLIS, MILLISECONDS))
                .as("first가 커밋하기 전에는 second가 Lock을 기다려야 한다")
                .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            firstFuture.get(10, SECONDS);

            return secondFuture.get(10, SECONDS);

        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private static Throwable capture(Runnable task) {
        try {
            task.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, SECONDS)) {
                throw new IllegalStateException("대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // ===================== 검증 헬퍼 =====================

    private static void assertOneSucceedsOthersFailWith(
        List<Throwable> results,
        ErrorCode expected
    ) {
        assertThat(results)
            .as("정확히 하나만 성공해야 한다")
            .filteredOn(Objects::isNull)
            .hasSize(1);

        assertThat(results)
            .filteredOn(Objects::nonNull)
            .allSatisfy(error -> assertBusinessError(error, expected));
    }

    private static void assertBusinessError(Throwable error, ErrorCode expected) {
        assertThat(error)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(expected);
    }

    private void inTransaction(Runnable assertion) {
        new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> assertion.run());
    }

    private SubstituteRequest findRequest(Long requestId) {
        return substituteRequestRepository.findById(requestId).orElseThrow();
    }

    private List<CandidateStatus> candidateStatuses(List<Long> candidateIds) {
        return candidateIds.stream()
            .map(id -> substituteCandidateRepository.findById(id).orElseThrow())
            .map(SubstituteCandidate::getStatus)
            .toList();
    }

    private long countActiveRequests(ShiftFixture fixture) {
        return new TransactionTemplate(transactionManager).execute(status ->
            substituteRequestRepository
                .findAllByRequesterMember_User_IdOrderByCreateDateDesc(fixture.requesterUserId())
                .stream()
                .filter(request -> request.getShift().getId().equals(fixture.shiftId()))
                .filter(request -> request.getStatus() == RequestStatus.OPEN
                    || request.getStatus() == RequestStatus.ACCEPTED)
                .count()
        );
    }

    // ===================== Fixture =====================

    // 요청 전 Shift 1개 + 대타 가능한 후보 1명
    private ShiftFixture createShiftFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = uniqueSuffix();
            Workplace workplace = saveWorkplace(suffix);
            Schedule schedule = savePublishedSchedule(workplace);

            WorkplaceMember requester = saveMember(workplace, "requester-" + suffix);
            saveMember(workplace, "candidate-" + suffix);

            Shift shift = saveShift(schedule, requester, shiftStartAt());

            return new ShiftFixture(requester.getUser().getId(), shift.getId());
        });
    }

    // OPEN Request 1개 + PENDING Candidate N명
    private RequestFixture createRequestFixture(int candidateCount) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = uniqueSuffix();
            Workplace workplace = saveWorkplace(suffix);
            Schedule schedule = savePublishedSchedule(workplace);

            WorkplaceMember requester = saveMember(workplace, "requester-" + suffix);
            Shift shift = saveShift(schedule, requester, shiftStartAt());

            SubstituteRequest request = substituteRequestRepository.save(
                new SubstituteRequest(shift, requester, RequestStatus.OPEN)
            );

            List<Long> candidateIds = new ArrayList<>();
            List<Long> candidateUserIds = new ArrayList<>();

            for (int i = 0; i < candidateCount; i++) {
                WorkplaceMember member = saveMember(workplace, "candidate" + i + "-" + suffix);

                SubstituteCandidate candidate = substituteCandidateRepository.save(
                    new SubstituteCandidate(request, member)
                );

                candidateIds.add(candidate.getId());
                candidateUserIds.add(member.getUser().getId());
            }

            return new RequestFixture(request.getId(), candidateIds, candidateUserIds);
        });
    }

    // 후보자 1명이 받은, 서로 시간이 겹치는 OPEN Request N개
    private SameUserFixture createSameUserFixture(int requestCount) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = uniqueSuffix();
            Workplace workplace = saveWorkplace(suffix);
            Schedule schedule = savePublishedSchedule(workplace);

            WorkplaceMember candidateMember = saveMember(workplace, "candidate-" + suffix);

            List<Long> requestIds = new ArrayList<>();
            List<Long> candidateIds = new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                WorkplaceMember requester = saveMember(workplace, "requester" + i + "-" + suffix);

                // 10분씩 어긋나지만 모두 4시간짜리라 서로 겹친다
                Shift shift = saveShift(schedule, requester, shiftStartAt().plusMinutes(10L * i));

                SubstituteRequest request = substituteRequestRepository.save(
                    new SubstituteRequest(shift, requester, RequestStatus.OPEN)
                );

                SubstituteCandidate candidate = substituteCandidateRepository.save(
                    new SubstituteCandidate(request, candidateMember)
                );

                requestIds.add(request.getId());
                candidateIds.add(candidate.getId());
            }

            return new SameUserFixture(requestIds, candidateIds, candidateMember.getUser().getId());
        });
    }

    private LocalDateTime now() {
        return LocalDateTime.now(testClock);
    }

    // 일주일 뒤 10:00
    private LocalDateTime shiftStartAt() {
        return now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    private Workplace saveWorkplace(String suffix) {
        return workplaceRepository.save(new Workplace("대타 동시성 테스트 매장 " + suffix, "SUB-" + suffix));
    }

    private Schedule savePublishedSchedule(Workplace workplace) {
        Schedule schedule = new Schedule(
            workplace,
            shiftStartAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        );
        schedule.publish(now());
        return scheduleRepository.save(schedule);
    }

    private WorkplaceMember saveMember(Workplace workplace, String name) {
        User user = userRepository.save(new User(name + "@test.com", "password-hash", name));

        return workplaceMemberRepository.save(
            new WorkplaceMember(workplace, user, WorkplaceRole.EMPLOYEE, now().minusDays(30))
        );
    }

    private Shift saveShift(Schedule schedule, WorkplaceMember member, LocalDateTime startAt) {
        return shiftRepository.save(
            new Shift(schedule, member, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED)
        );
    }

    // @RepeatedTest로 여러 번 만들어도 email / inviteCode가 겹치지 않도록
    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record ShiftFixture(Long requesterUserId, Long shiftId) {
    }

    private record RequestFixture(
        Long requestId,
        List<Long> candidateIds,
        List<Long> candidateUserIds
    ) {
    }

    private record SameUserFixture(
        List<Long> requestIds,
        List<Long> candidateIds,
        Long candidateUserId
    ) {
    }
}
