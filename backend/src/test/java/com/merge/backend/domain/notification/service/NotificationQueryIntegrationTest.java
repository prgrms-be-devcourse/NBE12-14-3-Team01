package com.merge.backend.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.merge.backend.domain.notification.dto.NotificationResponse;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 MySQL 목록 Query가 모든 현재 소속의 내 알림을 반환하는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig.class)
class NotificationQueryIntegrationTest {

    private static final Instant BASE_INSTANT = Instant.parse("2026-09-15T01:00:00Z");

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkplaceRepository workplaceRepository;

    @Autowired
    private WorkplaceMemberRepository memberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TestClock testClock;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void resetClock() {
        testClock.setInstant(BASE_INSTANT);
    }

    @Test
    @DisplayName("현재 소속된 모든 근무지의 내 알림만 반환하고 각 근무지 정보가 일치한다")
    void getNotificationsReturnsOwnNotificationsAcrossAllActiveWorkplaces() {
        // given - Fixture Transaction이 끝나면 Commit되어 관리 Entity를 넘기지 않는다
        Fixture fixture = fixture();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // 제외용 알림도 실제 존재해야 필터가 빠진 구현을 감지할 수 있다
            assertThat(entityManager.createQuery(
                    "select n.id from Notification n where n.recipientMember.id in :ids",
                    Long.class
                ).setParameter("ids", fixture.memberIds()).getResultList())
                .containsExactlyInAnyOrderElementsOf(fixture.allNotificationIds());
            entityManager.unwrap(Session.class).doWork(connection -> {
                var metadata = connection.getMetaData();
                assertThat(metadata.getDatabaseProductName()).isEqualTo("MySQL");
                assertThat(metadata.getDatabaseProductVersion()).startsWith("9.7.2");
                System.out.println("J4 DB=" + metadata.getDatabaseProductName()
                    + ", version=" + metadata.getDatabaseProductVersion()
                    + ", isolation=" + connection.getTransactionIsolation());
            });
        });

        // when - 바깥 Transaction 없이 실제 Service의 새 readOnly Transaction을 사용한다
        List<NotificationResponse> result = notificationService.getNotifications(fixture.userId());

        // then - 순서 계약은 J2에서 보호한다. 여기서는 정확한 합집합과 매핑을 확인한다
        assertThat(result).extracting(NotificationResponse::getNotificationId)
            .containsExactlyInAnyOrder(fixture.axId(), fixture.ayId());
        assertThat(result).extracting(
                NotificationResponse::getNotificationId,
                NotificationResponse::getWorkplaceId,
                NotificationResponse::getWorkplaceName
            )
            .containsExactlyInAnyOrder(
                tuple(fixture.axId(), fixture.xId(), fixture.xName()),
                tuple(fixture.ayId(), fixture.yId(), fixture.yName())
        );
        System.out.println("J4 committed fixture=5, result=AX+AY, excluded=AZ+BX+BW");
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            User a = userRepository.save(new User("j4-a-" + suffix + "@test.com", "pw", "A"));
            User b = userRepository.save(new User("j4-b-" + suffix + "@test.com", "pw", "B"));
            Workplace x = workplace("X", suffix);
            Workplace y = workplace("Y", suffix);
            Workplace z = workplace("Z", suffix);
            Workplace w = workplace("W", suffix);
            WorkplaceMember ax = member(a, x, null);
            WorkplaceMember ay = member(a, y, null);
            WorkplaceMember az = member(a, z, LocalDateTime.of(2026, 9, 10, 18, 0));
            WorkplaceMember bx = member(b, x, null);
            WorkplaceMember bw = member(b, w, null);
            Notification nAx = notification(ax);
            Notification nAy = notification(ay);
            Notification nAz = notification(az);
            Notification nBx = notification(bx);
            Notification nBw = notification(bw);
            return new Fixture(
                a.getId(), List.of(ax.getId(), ay.getId(), az.getId(), bx.getId(), bw.getId()),
                List.of(nAx.getId(), nAy.getId(), nAz.getId(), nBx.getId(), nBw.getId()),
                nAx.getId(), nAy.getId(), x.getId(), x.getName(), y.getId(), y.getName()
            );
        });
    }

    private Workplace workplace(String name, String suffix) {
        return workplaceRepository.save(new Workplace("J4 " + name + suffix, name + suffix));
    }

    private WorkplaceMember member(User user, Workplace workplace, LocalDateTime leftAt) {
        return memberRepository.save(new WorkplaceMember(
            workplace, user, WorkplaceRole.EMPLOYEE, LocalDateTime.of(2026, 9, 1, 9, 0), leftAt
        ));
    }

    private Notification notification(WorkplaceMember member) {
        return notificationRepository.save(new Notification(
            member, NotificationType.SCHEDULE_PUBLISHED, "J4 목록 조회 Fixture"
        ));
    }

    private record Fixture(
        Long userId,
        List<Long> memberIds,
        List<Long> allNotificationIds,
        Long axId,
        Long ayId,
        Long xId,
        String xName,
        Long yId,
        String yName
    ) {
    }
}
