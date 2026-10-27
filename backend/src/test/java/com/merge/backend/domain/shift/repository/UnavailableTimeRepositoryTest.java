package com.merge.backend.domain.shift.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.merge.backend.domain.shift.entity.Schedule;
import com.merge.backend.domain.shift.entity.Shift;
import com.merge.backend.domain.shift.entity.ShiftStatus;
import com.merge.backend.domain.shift.entity.UnavailableTime;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.global.config.TimeConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TimeConfig.class)
class UnavailableTimeRepositoryTest {

    private final UnavailableTimeRepository unavailableTimeRepository;
    private final TestEntityManager entityManager;

    @Autowired
    UnavailableTimeRepositoryTest(
        UnavailableTimeRepository unavailableTimeRepository,
        TestEntityManager entityManager
    ) {
        this.unavailableTimeRepository = unavailableTimeRepository;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("겹치는 근무 불가능 일정이 존재하면 true를 반환한다")
    void existsOverlappingUnavailableTimeReturnsTrueWhenOverlapExists() {

        // given
        User user = entityManager.persist(
            new User(
                "user@test.com",
                "password",
                "사용자"
            )
        );

        UnavailableTime unavailableTime =
            new UnavailableTime(
                user,
                LocalDateTime.of(
                    2026, 9, 22, 10, 0
                ),
                LocalDateTime.of(
                    2026, 9, 22, 12, 0
                )
            );

        entityManager.persist(unavailableTime);
        entityManager.flush();
        entityManager.clear();

        LocalDateTime newStartAt =
            LocalDateTime.of(
                2026, 9, 22, 11, 0
            );

        LocalDateTime newEndAt =
            LocalDateTime.of(
                2026, 9, 22, 13, 0
            );

        // when
        boolean result =
            unavailableTimeRepository
                .existsOverlappingUnavailableTime(
                    user.getId(),
                    newStartAt,
                    newEndAt
                );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("기존 일정 종료와 새 일정 시작이 같으면 false를 반환한다")
    void existsOverlappingUnavailableTimeReturnsFalseWhenBoundaryTouches() {

        // given
        User user = entityManager.persist(
            new User(
                "boundary@test.com",
                "password",
                "사용자"
            )
        );

        UnavailableTime unavailableTime =
            new UnavailableTime(
                user,
                LocalDateTime.of(
                    2026, 9, 22, 10, 0
                ),
                LocalDateTime.of(
                    2026, 9, 22, 12, 0
                )
            );

        entityManager.persist(unavailableTime);
        entityManager.flush();
        entityManager.clear();

        LocalDateTime newStartAt =
            LocalDateTime.of(
                2026, 9, 22, 12, 0
            );

        LocalDateTime newEndAt =
            LocalDateTime.of(
                2026, 9, 22, 14, 0
            );

        // when
        boolean result =
            unavailableTimeRepository
                .existsOverlappingUnavailableTime(
                    user.getId(),
                    newStartAt,
                    newEndAt
                );

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("수정 시 자기 자신을 제외하면 겹치는 일정이 없어 false를 반환한다")
    void existsOverlappingUnavailableTimeExcludingIdReturnsFalseForItself() {

        // given
        User user = entityManager.persist(
            new User(
                "exclude@test.com",
                "password",
                "사용자"
            )
        );

        UnavailableTime unavailableTime =
            new UnavailableTime(
                user,
                LocalDateTime.of(
                    2026, 9, 22, 10, 0
                ),
                LocalDateTime.of(
                    2026, 9, 22, 12, 0
                )
            );

        entityManager.persist(unavailableTime);

        Long userId = user.getId();
        Long unavailableTimeId = unavailableTime.getId();

        entityManager.flush();
        entityManager.clear();

        LocalDateTime newStartAt =
            LocalDateTime.of(
                2026, 9, 22, 11, 0
            );

        LocalDateTime newEndAt =
            LocalDateTime.of(
                2026, 9, 22, 13, 0
            );

        // when
        boolean result =
            unavailableTimeRepository
                .existsOverlappingUnavailableTimeExcludingId(
                    userId,
                    unavailableTimeId,
                    newStartAt,
                    newEndAt
                );

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("수정 시 자기 자신을 제외해도 다른 일정과 겹치면 true를 반환한다")
    void existsOverlappingUnavailableTimeExcludingIdReturnsTrueForAnotherOverlap() {

        // given
        User user = entityManager.persist(
            new User(
                "another-overlap@test.com",
                "password",
                "사용자"
            )
        );

        UnavailableTime target =
            new UnavailableTime(
                user,
                LocalDateTime.of(
                    2026, 9, 22, 10, 0
                ),
                LocalDateTime.of(
                    2026, 9, 22, 12, 0
                )
            );

        entityManager.persist(target);

        UnavailableTime another =
            new UnavailableTime(
                user,
                LocalDateTime.of(
                    2026, 9, 22, 12, 30
                ),
                LocalDateTime.of(
                    2026, 9, 22, 14, 0
                )
            );

        entityManager.persist(another);

        Long userId = user.getId();
        Long targetId = target.getId();

        entityManager.flush();
        entityManager.clear();

        LocalDateTime newStartAt =
            LocalDateTime.of(
                2026, 9, 22, 11, 0
            );

        LocalDateTime newEndAt =
            LocalDateTime.of(
                2026, 9, 22, 13, 0
            );

        // when
        boolean result =
            unavailableTimeRepository
                .existsOverlappingUnavailableTimeExcludingId(
                    userId,
                    targetId,
                    newStartAt,
                    newEndAt
                );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("현재/미래 일정 중 공식 Shift와 충돌하는 일정 ID만 조회한다")
    void findIdsWithOfficialShiftConflictReturnsOnlyOfficialConflicts() {

        // given
        LocalDateTime now =
            LocalDateTime.of(
                2026, 9, 21, 12, 0
            );

        User user = entityManager.persist(
            new User(
                "bulk-conflict@test.com",
                "password",
                "사용자"
            )
        );

        Workplace workplace = entityManager.persist(
            new Workplace(
                "SWITCH 카페",
                "BULK1234"
            )
        );

        WorkplaceMember member =
            entityManager.persist(
                new WorkplaceMember(
                    workplace,
                    user,
                    WorkplaceRole.EMPLOYEE,
                    LocalDateTime.of(
                        2026, 9, 1, 9, 0
                    )
                )
            );

        // 공식 Schedule
        Schedule publishedSchedule =
            new Schedule(
                workplace,
                LocalDate.of(2026, 9, 21)
            );

        publishedSchedule.publish(
            now.minusDays(1)
        );

        entityManager.persist(
            publishedSchedule
        );

        // 아직 공식이 아닌 DRAFT Schedule
        Schedule draftSchedule =
            new Schedule(
                workplace,
                LocalDate.of(2026, 9, 28)
            );

        entityManager.persist(
            draftSchedule
        );

        // 공식 Shift와 겹칠 UnavailableTime
        UnavailableTime officialConflict =
            entityManager.persist(
                new UnavailableTime(
                    user,
                    LocalDateTime.of(
                        2026, 9, 22, 10, 0
                    ),
                    LocalDateTime.of(
                        2026, 9, 22, 12, 0
                    )
                )
            );

        // DRAFT Shift와만 겹칠 UnavailableTime
        UnavailableTime draftConflict =
            entityManager.persist(
                new UnavailableTime(
                    user,
                    LocalDateTime.of(
                        2026, 9, 29, 10, 0
                    ),
                    LocalDateTime.of(
                        2026, 9, 29, 12, 0
                    )
                )
            );

        // PUBLISHED + SCHEDULED
        entityManager.persist(
            new Shift(
                publishedSchedule,
                member,
                LocalDateTime.of(
                    2026, 9, 22, 11, 0
                ),
                LocalDateTime.of(
                    2026, 9, 22, 13, 0
                ),
                ShiftStatus.SCHEDULED
            )
        );

        // 시간은 겹치지만 Schedule이 DRAFT
        entityManager.persist(
            new Shift(
                draftSchedule,
                member,
                LocalDateTime.of(
                    2026, 9, 29, 11, 0
                ),
                LocalDateTime.of(
                    2026, 9, 29, 13, 0
                ),
                ShiftStatus.SCHEDULED
            )
        );

        Long userId = user.getId();
        Long officialConflictId =
            officialConflict.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        List<Long> result =
            unavailableTimeRepository
                .findIdsWithOfficialShiftConflict(
                    userId,
                    now
                );

        // then
        assertThat(result)
            .containsExactly(
                officialConflictId
            );
    }

    @Test
    @DisplayName("사용자의 미래 일정만 시작 시간 오름차순으로 조회한다")
    void findByUserIdAndEndAtAfterOrderByStartAtAscReturnsOnlyFutureTimesInOrder() {

        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 12, 0);

        User user = entityManager.persist(
            new User(
                "future-order@test.com",
                "password",
                "사용자"
            )
        );

        User otherUser = entityManager.persist(
            new User(
                "other-future-order@test.com",
                "password",
                "다른 사용자"
            )
        );

        UnavailableTime futureLate = entityManager.persist(
            new UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 21, 16, 0),
                LocalDateTime.of(2026, 9, 21, 17, 0)
            )
        );

        entityManager.persist(
            new UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 21, 10, 0),
                now
            )
        );

        entityManager.persist(
            new UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 21, 9, 0),
                LocalDateTime.of(2026, 9, 21, 11, 0)
            )
        );

        UnavailableTime futureEarly = entityManager.persist(
            new UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 21, 13, 0),
                LocalDateTime.of(2026, 9, 21, 14, 0)
            )
        );

        entityManager.persist(
            new UnavailableTime(
                otherUser,
                LocalDateTime.of(2026, 9, 21, 12, 30),
                LocalDateTime.of(2026, 9, 21, 13, 30)
            )
        );

        Long userId = user.getId();
        Long futureEarlyId = futureEarly.getId();
        Long futureLateId = futureLate.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        List<UnavailableTime> result =
            unavailableTimeRepository
                .findByUserIdAndEndAtAfterOrderByStartAtAsc(
                    userId,
                    now
                );

        // then
        assertThat(result)
            .extracting(UnavailableTime::getId)
            .containsExactly(
                futureEarlyId,
                futureLateId
            );
    }

    @Test
    @DisplayName("공개 근무표의 CANCELLED Shift와만 겹치는 일정은 공식 충돌에서 제외한다")
    void findIdsWithOfficialShiftConflictExcludesCancelledShiftOnlyConflict() {

        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 12, 0);

        User user = entityManager.persist(
            new User(
                "cancelled-shift@test.com",
                "password",
                "사용자"
            )
        );

        Workplace workplace = entityManager.persist(
            new Workplace(
                "SWITCH 카페",
                "CANCEL123"
            )
        );

        WorkplaceMember member = entityManager.persist(
            new WorkplaceMember(
                workplace,
                user,
                WorkplaceRole.EMPLOYEE,
                LocalDateTime.of(2026, 9, 1, 9, 0)
            )
        );

        Schedule publishedSchedule = new Schedule(
            workplace,
            LocalDate.of(2026, 9, 21)
        );

        publishedSchedule.publish(now.minusDays(1));
        entityManager.persist(publishedSchedule);

        // SCHEDULED Shift와 겹치는 불가능 일정
        UnavailableTime scheduledShiftConflict = entityManager.persist(
            new UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 22, 10, 0),
                LocalDateTime.of(2026, 9, 22, 12, 0)
            )
        );

        entityManager.persist(
            new Shift(
                publishedSchedule,
                member,
                LocalDateTime.of(2026, 9, 22, 11, 0),
                LocalDateTime.of(2026, 9, 22, 13, 0),
                ShiftStatus.SCHEDULED
            )
        );

        // CANCELLED Shift와만 겹치는 불가능 일정
        UnavailableTime cancelledShiftOnlyConflict = entityManager.persist(
            new UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 23, 10, 0),
                LocalDateTime.of(2026, 9, 23, 12, 0)
            )
        );

        entityManager.persist(
            new Shift(
                publishedSchedule,
                member,
                LocalDateTime.of(2026, 9, 23, 11, 0),
                LocalDateTime.of(2026, 9, 23, 13, 0),
                ShiftStatus.CANCELLED
            )
        );

        Long userId = user.getId();
        Long scheduledShiftConflictId = scheduledShiftConflict.getId();
        Long cancelledShiftOnlyConflictId = cancelledShiftOnlyConflict.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        List<Long> result = unavailableTimeRepository
            .findIdsWithOfficialShiftConflict(
                userId,
                now
            );

        // then
        assertThat(result)
            .containsExactly(scheduledShiftConflictId)
            .doesNotContain(cancelledShiftOnlyConflictId);
    }

    @Test
    @DisplayName("근무 불가능 일정 수정은 변경 감지로 DB에 반영되고 사용자 연관관계를 유지한다")
    void updatePersistsChangedTimesAfterFlushAndClear() {

        // given
        User user = entityManager.persist(
            new User(
                "dirty-checking@test.com",
                "password",
                "사용자"
            )
        );

        LocalDateTime originalStartAt = LocalDateTime.of(2026, 9, 22, 10, 0);
        LocalDateTime originalEndAt = LocalDateTime.of(2026, 9, 22, 12, 0);

        UnavailableTime unavailableTime = entityManager.persist(
            new UnavailableTime(
                user,
                originalStartAt,
                originalEndAt
            )
        );

        Long unavailableTimeId = unavailableTime.getId();
        Long userId = user.getId();

        entityManager.flush();
        entityManager.clear();

        LocalDateTime changedStartAt = LocalDateTime.of(2026, 9, 23, 13, 0);
        LocalDateTime changedEndAt = LocalDateTime.of(2026, 9, 23, 15, 0);

        UnavailableTime managedUnavailableTime = entityManager.find(
            UnavailableTime.class,
            unavailableTimeId
        );

        // when
        managedUnavailableTime.update(
            changedStartAt,
            changedEndAt
        );

        entityManager.flush();
        entityManager.clear();

        UnavailableTime reloadedUnavailableTime = entityManager.find(
            UnavailableTime.class,
            unavailableTimeId
        );

        // then
        assertThat(reloadedUnavailableTime.getStartAt())
            .isEqualTo(changedStartAt);

        assertThat(reloadedUnavailableTime.getEndAt())
            .isEqualTo(changedEndAt);

        assertThat(reloadedUnavailableTime.getUser().getId())
            .isEqualTo(userId);
    }

}
