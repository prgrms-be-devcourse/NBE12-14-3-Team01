package com.merge.backend.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.merge.backend.domain.notification.dto.NotificationReadResponse;
import com.merge.backend.domain.notification.entity.Notification;
import com.merge.backend.domain.notification.entity.NotificationType;
import com.merge.backend.domain.notification.repository.NotificationRepository;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.user.repository.UserRepository;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.domain.workplace.repository.WorkplaceRepository;
import com.merge.backend.global.config.TestClock;
import com.merge.backend.global.config.TestTimeConfig;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
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
class NotificationReadConcurrencyTest {
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

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

    @Test
    @DisplayName(
        "같은 알림을 동시에 읽음 처리해도 최초 readAt을 유지한다"
    )
    void concurrentReadPreservesFirstReadAt()
        throws Exception {

        // given
        Fixture fixture =
            createFixture("same-notification");

        /*
         * 첫 번째 요청 시각
         *
         * 2026-09-15T01:00:00Z
         * = Asia/Seoul 기준 2026-09-15 10:00
         */
        testClock.setInstant(
            Instant.parse("2026-09-15T01:00:00Z")
        );

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        /*
         * 첫 번째 요청이:
         *
         * Notification Lock 획득
         * +
         * readAt 변경
         *
         * 까지 완료했음을 알려주는 Latch
         */
        CountDownLatch firstMarked =
            new CountDownLatch(1);

        /*
         * 첫 번째 Transaction을
         * 언제 commit시킬지 제어하는 Latch
         */
        CountDownLatch releaseFirst =
            new CountDownLatch(1);

        /*
         * 두 번째 작업 Thread 자체가
         * 시작됐음을 확인하기 위한 Latch
         */
        CountDownLatch secondStarted =
            new CountDownLatch(1);

        try {

            /*
             * 첫 번째 요청
             *
             * 바깥쪽 TransactionTemplate을 사용해서
             * NotificationService.markAsRead()가 끝난 뒤에도
             * Transaction을 commit하지 않고 유지한다.
             *
             * 따라서 PESSIMISTIC_WRITE Lock도 계속 유지된다.
             */
            Future<NotificationReadResponse> firstFuture =
                executor.submit(() -> {

                    TransactionTemplate transactionTemplate =
                        new TransactionTemplate(
                            transactionManager
                        );

                    return transactionTemplate.execute(status -> {

                        NotificationReadResponse result =
                            notificationService.markAsRead(
                                fixture.userId(),
                                fixture.notificationId()
                            );

                        /*
                         * 여기까지 왔다면:
                         *
                         * - Notification Lock 획득
                         * - readAt = 10:00 설정
                         *
                         * 은 완료됐지만,
                         * 아직 바깥 Transaction은 commit되지 않았다.
                         */
                        firstMarked.countDown();

                        try {
                            /*
                             * 테스트 Thread가 허용할 때까지
                             * 첫 번째 Transaction을 유지한다.
                             */
                            releaseFirst.await();

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();

                            throw new RuntimeException(e);
                        }

                        return result;
                    });
                });

            /*
             * 첫 번째 요청이 실제 readAt 변경까지
             * 완료했는지 기다린다.
             */
            assertThat(
                firstMarked.await(
                    3,
                    TimeUnit.SECONDS
                )
            ).isTrue();

            /*
             * 두 번째 요청 시각은 5분 뒤로 변경한다.
             *
             * 2026-09-15T01:05:00Z
             * = Asia/Seoul 기준 10:05
             *
             * 만약 두 번째 요청이 readAt을 덮어쓴다면
             * 10:05가 저장될 것이다.
             */
            testClock.setInstant(
                Instant.parse("2026-09-15T01:05:00Z")
            );

            /*
             * 두 번째 요청
             */
            Future<NotificationReadResponse> secondFuture =
                executor.submit(() -> {

                    secondStarted.countDown();

                    return notificationService.markAsRead(
                        fixture.userId(),
                        fixture.notificationId()
                    );
                });

            /*
             * 두 번째 Thread 자체가 시작됐는지 확인한다.
             */
            assertThat(
                secondStarted.await(
                    3,
                    TimeUnit.SECONDS
                )
            ).isTrue();

            /*
             * 아직 첫 번째 Transaction이
             * Notification Lock을 가지고 있다.
             *
             * 따라서 두 번째 요청은 같은 Notification의
             * PESSIMISTIC_WRITE Lock을 얻지 못하고
             * 기다려야 한다.
             *
             * 500ms 안에 결과가 나오면 안 된다.
             */
            assertThatThrownBy(() ->
                secondFuture.get(
                    500,
                    TimeUnit.MILLISECONDS
                )
            ).isInstanceOf(
                TimeoutException.class
            );

            /*
             * 이제 첫 번째 Transaction을 끝낼 수 있게 한다.
             *
             * callback 종료
             * → Transaction commit
             * → Lock 해제
             */
            releaseFirst.countDown();

            NotificationReadResponse firstResult =
                firstFuture.get(
                    5,
                    TimeUnit.SECONDS
                );

            NotificationReadResponse secondResult =
                secondFuture.get(
                    5,
                    TimeUnit.SECONDS
                );

            /*
             * 최초 읽음 시각.
             *
             * 첫 번째 요청 당시 Clock은
             * 한국시간 10:00이었다.
             */
            LocalDateTime expectedReadAt =
                LocalDateTime.of(
                    2026, 9, 15,
                    10, 0
                );

            /*
             * 첫 번째 요청은 최초 읽음이므로
             * 10:00을 반환해야 한다.
             */
            assertThat(firstResult.getReadAt())
                .isEqualTo(expectedReadAt);

            /*
             * 두 번째 요청 당시 현재 Clock은
             * 이미 10:05다.
             *
             * 그래도 기존 readAt이 있으므로
             * 10:05로 덮어쓰지 않고
             * 최초 10:00을 반환해야 한다.
             */
            assertThat(secondResult.getReadAt())
                .isEqualTo(expectedReadAt);

            /*
             * 두 응답 모두 같은 Notification에 대한 결과인지도 확인한다.
             */
            assertThat(firstResult.getNotificationId())
                .isEqualTo(fixture.notificationId());

            assertThat(secondResult.getNotificationId())
                .isEqualTo(fixture.notificationId());

            /*
             * 마지막으로 Response뿐 아니라
             * 실제 DB 상태도 확인한다.
             */
            TransactionTemplate verifyTransaction =
                new TransactionTemplate(
                    transactionManager
                );

            LocalDateTime finalReadAt =
                verifyTransaction.execute(status ->
                    notificationRepository
                        .findById(
                            fixture.notificationId()
                        )
                        .orElseThrow()
                        .getReadAt()
                );

            /*
             * DB에도 최초 시각 10:00만 남아 있어야 한다.
             */
            assertThat(finalReadAt)
                .isEqualTo(expectedReadAt);

        } finally {

            /*
             * 중간 assertion에서 실패하더라도
             * 첫 번째 Thread가 releaseFirst.await()에서
             * 계속 대기하지 않도록 반드시 풀어준다.
             */
            releaseFirst.countDown();

            executor.shutdownNow();
        }
    }

    private Fixture createFixture(String suffix) {

        TransactionTemplate transactionTemplate =
            new TransactionTemplate(transactionManager);

        return transactionTemplate.execute(status -> {

            User user =
                userRepository.save(
                    new User(
                        "notification-read-" + suffix + "@test.com",
                        "password-hash",
                        "notification-user"
                    )
                );

            Workplace workplace =
                workplaceRepository.save(
                    new Workplace(
                        "알림 읽음 동시성 테스트 매장 " + suffix,
                        "NOT-READ-" + suffix
                    )
                );

            LocalDateTime joinedAt =
                LocalDateTime.of(
                    2026, 9, 1,
                    9, 0
                );

            WorkplaceMember member =
                workplaceMemberRepository.save(
                    new WorkplaceMember(
                        workplace,
                        user,
                        WorkplaceRole.EMPLOYEE,
                        joinedAt
                    )
                );

            Notification notification =
                notificationRepository.save(
                    new Notification(
                        member,
                        NotificationType.SCHEDULE_PUBLISHED,
                        "알림 읽음 동시성 테스트"
                    )
                );

            return new Fixture(
                user.getId(),
                notification.getId()
            );
        });
    }

    private record Fixture(
        Long userId,
        Long notificationId
    ) {
    }
}