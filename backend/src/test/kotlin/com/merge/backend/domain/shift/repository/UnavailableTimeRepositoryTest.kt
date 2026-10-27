package com.merge.backend.domain.shift.repository

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.entity.UnavailableTime
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.global.config.TimeConfig
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(TimeConfig::class)
class UnavailableTimeRepositoryTest @Autowired constructor(
    private val unavailableTimeRepository: UnavailableTimeRepository,
    private val entityManager: TestEntityManager,
) {

    @Test
    @DisplayName("겹치는 근무 불가능 일정이 존재하면 true를 반환한다")
    fun existsOverlappingUnavailableTimeReturnsTrueWhenOverlapExists() {
        // given
        val user = entityManager.persist(User("user@test.com", "password", "사용자"))
        entityManager.persist(
            UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 22, 10, 0),
                LocalDateTime.of(2026, 9, 22, 12, 0),
            )
        )
        entityManager.flush()
        entityManager.clear()

        val newStartAt = LocalDateTime.of(2026, 9, 22, 11, 0)
        val newEndAt = LocalDateTime.of(2026, 9, 22, 13, 0)

        // when
        val result = unavailableTimeRepository.existsOverlappingUnavailableTime(user.id, newStartAt, newEndAt)

        // then
        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("기존 일정 종료와 새 일정 시작이 같으면 false를 반환한다")
    fun existsOverlappingUnavailableTimeReturnsFalseWhenBoundaryTouches() {
        // given
        val user = entityManager.persist(User("boundary@test.com", "password", "사용자"))
        entityManager.persist(
            UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 22, 10, 0),
                LocalDateTime.of(2026, 9, 22, 12, 0),
            )
        )
        entityManager.flush()
        entityManager.clear()

        val newStartAt = LocalDateTime.of(2026, 9, 22, 12, 0)
        val newEndAt = LocalDateTime.of(2026, 9, 22, 14, 0)

        // when
        val result = unavailableTimeRepository.existsOverlappingUnavailableTime(user.id, newStartAt, newEndAt)

        // then
        assertThat(result).isFalse()
    }

    @Test
    @DisplayName("수정 시 자기 자신을 제외하면 겹치는 일정이 없어 false를 반환한다")
    fun existsOverlappingUnavailableTimeExcludingIdReturnsFalseForItself() {
        // given
        val user = entityManager.persist(User("exclude@test.com", "password", "사용자"))
        val unavailableTime = entityManager.persist(
            UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 22, 10, 0),
                LocalDateTime.of(2026, 9, 22, 12, 0),
            )
        )
        val userId = user.id
        val unavailableTimeId = unavailableTime.id
        entityManager.flush()
        entityManager.clear()

        val newStartAt = LocalDateTime.of(2026, 9, 22, 11, 0)
        val newEndAt = LocalDateTime.of(2026, 9, 22, 13, 0)

        // when
        val result = unavailableTimeRepository.existsOverlappingUnavailableTimeExcludingId(
            userId,
            unavailableTimeId,
            newStartAt,
            newEndAt,
        )

        // then
        assertThat(result).isFalse()
    }

    @Test
    @DisplayName("수정 시 자기 자신을 제외해도 다른 일정과 겹치면 true를 반환한다")
    fun existsOverlappingUnavailableTimeExcludingIdReturnsTrueForAnotherOverlap() {
        // given
        val user = entityManager.persist(User("another-overlap@test.com", "password", "사용자"))
        val target = entityManager.persist(
            UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 22, 10, 0),
                LocalDateTime.of(2026, 9, 22, 12, 0),
            )
        )
        entityManager.persist(
            UnavailableTime(
                user,
                LocalDateTime.of(2026, 9, 22, 12, 30),
                LocalDateTime.of(2026, 9, 22, 14, 0),
            )
        )
        val userId = user.id
        val targetId = target.id
        entityManager.flush()
        entityManager.clear()

        val newStartAt = LocalDateTime.of(2026, 9, 22, 11, 0)
        val newEndAt = LocalDateTime.of(2026, 9, 22, 13, 0)

        // when
        val result = unavailableTimeRepository.existsOverlappingUnavailableTimeExcludingId(
            userId,
            targetId,
            newStartAt,
            newEndAt,
        )

        // then
        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("현재/미래 일정 중 공식 Shift와 충돌하는 일정 ID만 조회한다")
    fun findIdsWithOfficialShiftConflictReturnsOnlyOfficialConflicts() {
        // given
        val now = LocalDateTime.of(2026, 9, 21, 12, 0)
        val user = entityManager.persist(User("bulk-conflict@test.com", "password", "사용자"))
        val workplace = entityManager.persist(Workplace("SWITCH 카페", "BULK1234"))
        val member = entityManager.persist(
            WorkplaceMember(user = user, workplace = workplace, role = WorkplaceRole.EMPLOYEE, joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0))
        )
        val publishedSchedule = Schedule(workplace, LocalDate.of(2026, 9, 21)).apply {
            publish(now.minusDays(1))
        }
        entityManager.persist(publishedSchedule)
        val draftSchedule = Schedule(workplace, LocalDate.of(2026, 9, 28))
        entityManager.persist(draftSchedule)

        val officialConflict = entityManager.persist(
            UnavailableTime(user, LocalDateTime.of(2026, 9, 22, 10, 0), LocalDateTime.of(2026, 9, 22, 12, 0))
        )
        entityManager.persist(
            UnavailableTime(user, LocalDateTime.of(2026, 9, 29, 10, 0), LocalDateTime.of(2026, 9, 29, 12, 0))
        )
        entityManager.persist(
            Shift(
                publishedSchedule,
                member,
                LocalDateTime.of(2026, 9, 22, 11, 0),
                LocalDateTime.of(2026, 9, 22, 13, 0),
                ShiftStatus.SCHEDULED,
            )
        )
        entityManager.persist(
            Shift(
                draftSchedule,
                member,
                LocalDateTime.of(2026, 9, 29, 11, 0),
                LocalDateTime.of(2026, 9, 29, 13, 0),
                ShiftStatus.SCHEDULED,
            )
        )
        val userId = user.id
        val officialConflictId = officialConflict.id
        entityManager.flush()
        entityManager.clear()

        // when
        val result = unavailableTimeRepository.findIdsWithOfficialShiftConflict(userId, now)

        // then
        assertThat(result).containsExactly(officialConflictId)
    }

    @Test
    @DisplayName("사용자의 종료되지 않은 일정만 시작 시간 오름차순으로 조회한다")
    fun findByUserIdAndEndAtAfterOrderByStartAtAscReturnsOnlyFutureTimesInOrder() {
        // given
        val now = LocalDateTime.of(2026, 9, 21, 12, 0)
        val user = entityManager.persist(User("future-order@test.com", "password", "사용자"))
        val otherUser = entityManager.persist(User("other-future-order@test.com", "password", "다른 사용자"))
        val futureLate = entityManager.persist(
            UnavailableTime(user, LocalDateTime.of(2026, 9, 21, 16, 0), LocalDateTime.of(2026, 9, 21, 17, 0))
        )
        entityManager.persist(UnavailableTime(user, LocalDateTime.of(2026, 9, 21, 10, 0), now))
        entityManager.persist(
            UnavailableTime(user, LocalDateTime.of(2026, 9, 21, 9, 0), LocalDateTime.of(2026, 9, 21, 11, 0))
        )
        val futureEarly = entityManager.persist(
            UnavailableTime(user, LocalDateTime.of(2026, 9, 21, 13, 0), LocalDateTime.of(2026, 9, 21, 14, 0))
        )
        entityManager.persist(
            UnavailableTime(otherUser, LocalDateTime.of(2026, 9, 21, 12, 30), LocalDateTime.of(2026, 9, 21, 13, 30))
        )
        val userId = user.id
        val futureEarlyId = futureEarly.id
        val futureLateId = futureLate.id
        entityManager.flush()
        entityManager.clear()

        // when
        val result = unavailableTimeRepository.findByUserIdAndEndAtAfterOrderByStartAtAsc(userId, now)

        // then
        assertThat(result)
            .extracting<Long?> { it.id }
            .containsExactly(futureEarlyId, futureLateId)
    }

    @Test
    @DisplayName("공개 근무표의 CANCELLED Shift와만 겹치는 일정은 공식 충돌에서 제외한다")
    fun findIdsWithOfficialShiftConflictExcludesCancelledShiftOnlyConflict() {
        // given
        val now = LocalDateTime.of(2026, 9, 21, 12, 0)
        val user = entityManager.persist(User("cancelled-shift@test.com", "password", "사용자"))
        val workplace = entityManager.persist(Workplace("SWITCH 카페", "CANCEL123"))
        val member = entityManager.persist(
            WorkplaceMember(user = user, workplace = workplace, role = WorkplaceRole.EMPLOYEE, joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0))
        )
        val publishedSchedule = Schedule(workplace, LocalDate.of(2026, 9, 21)).apply {
            publish(now.minusDays(1))
        }
        entityManager.persist(publishedSchedule)
        val scheduledShiftConflict = entityManager.persist(
            UnavailableTime(user, LocalDateTime.of(2026, 9, 22, 10, 0), LocalDateTime.of(2026, 9, 22, 12, 0))
        )
        entityManager.persist(
            Shift(
                publishedSchedule,
                member,
                LocalDateTime.of(2026, 9, 22, 11, 0),
                LocalDateTime.of(2026, 9, 22, 13, 0),
                ShiftStatus.SCHEDULED,
            )
        )
        val cancelledShiftOnlyConflict = entityManager.persist(
            UnavailableTime(user, LocalDateTime.of(2026, 9, 23, 10, 0), LocalDateTime.of(2026, 9, 23, 12, 0))
        )
        entityManager.persist(
            Shift(
                publishedSchedule,
                member,
                LocalDateTime.of(2026, 9, 23, 11, 0),
                LocalDateTime.of(2026, 9, 23, 13, 0),
                ShiftStatus.CANCELLED,
            )
        )
        val userId = user.id
        val scheduledShiftConflictId = scheduledShiftConflict.id
        val cancelledShiftOnlyConflictId = cancelledShiftOnlyConflict.id
        entityManager.flush()
        entityManager.clear()

        // when
        val result = unavailableTimeRepository.findIdsWithOfficialShiftConflict(userId, now)

        // then
        assertThat(result)
            .containsExactly(scheduledShiftConflictId)
            .doesNotContain(cancelledShiftOnlyConflictId)
    }

    @Test
    @DisplayName("근무 불가능 일정 수정은 변경 감지로 DB에 반영되고 사용자 연관관계를 유지한다")
    fun updatePersistsChangedTimesAfterFlushAndClear() {
        // given
        val user = entityManager.persist(User("dirty-checking@test.com", "password", "사용자"))
        val originalStartAt = LocalDateTime.of(2026, 9, 22, 10, 0)
        val originalEndAt = LocalDateTime.of(2026, 9, 22, 12, 0)
        val unavailableTime = entityManager.persist(UnavailableTime(user, originalStartAt, originalEndAt))
        val unavailableTimeId = unavailableTime.id
        val userId = user.id
        entityManager.flush()
        entityManager.clear()

        val changedStartAt = LocalDateTime.of(2026, 9, 23, 13, 0)
        val changedEndAt = LocalDateTime.of(2026, 9, 23, 15, 0)
        val managedUnavailableTime = requireNotNull(
            entityManager.find(UnavailableTime::class.java, requireNotNull(unavailableTimeId))
        )

        // when
        managedUnavailableTime.update(changedStartAt, changedEndAt)
        entityManager.flush()
        entityManager.clear()
        val reloadedUnavailableTime = requireNotNull(
            entityManager.find(UnavailableTime::class.java, requireNotNull(unavailableTimeId))
        )

        // then
        assertThat(reloadedUnavailableTime.startAt).isEqualTo(changedStartAt)
        assertThat(reloadedUnavailableTime.endAt).isEqualTo(changedEndAt)
        assertThat(reloadedUnavailableTime.user.id).isEqualTo(userId)
    }
}
