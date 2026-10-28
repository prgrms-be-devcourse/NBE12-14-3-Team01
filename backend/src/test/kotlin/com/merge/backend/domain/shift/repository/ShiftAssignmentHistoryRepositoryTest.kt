package com.merge.backend.domain.shift.repository

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftAssignmentHistory
import com.merge.backend.domain.shift.entity.ShiftAssignmentReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.global.config.TimeConfig
import com.merge.backend.global.config.TestTimeConfig
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig::class, TestTimeConfig::class)
class ShiftAssignmentHistoryRepositoryTest @Autowired constructor(
    private val shiftAssignmentHistoryRepository: ShiftAssignmentHistoryRepository,
    private val entityManager: TestEntityManager,
) {

    @Test
    fun 배정_이력은_근무와_이전_현재_담당자_그리고_행위자를_보존한다() {
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = entityManager.persist(Workplace("SWITCH 카페", "TEST1234"))
        val manager = persistMember(workplace, "manager@test.com", "관리자", WorkplaceRole.MANAGER, now)
        val previousMember = persistMember(workplace, "previous@test.com", "이전 담당자", WorkplaceRole.EMPLOYEE, now)
        val newMember = persistMember(workplace, "new@test.com", "새 담당자", WorkplaceRole.EMPLOYEE, now)
        val schedule = entityManager.persist(Schedule(workplace, LocalDate.of(2026, 10, 26)))
        val shift = entityManager.persist(
            Shift(
                schedule,
                previousMember,
                LocalDateTime.of(2026, 10, 30, 9, 0),
                LocalDateTime.of(2026, 10, 30, 18, 0),
                ShiftStatus.SCHEDULED
            )
        )
        val history = entityManager.persist(
            ShiftAssignmentHistory(
                shift,
                previousMember,
                newMember,
                ShiftAssignmentReason.MANAGER_REASSIGNED,
                manager
            )
        )
        entityManager.flush()
        entityManager.clear()

        val histories = shiftAssignmentHistoryRepository.findAllByShift_IdOrderByCreateDateAsc(
            requireNotNull(shift.id)
        )
        val savedHistory = histories.single()

        assertThat(savedHistory.id).isEqualTo(history.id)
        assertThat(savedHistory.shift.id).isEqualTo(shift.id)
        assertThat(savedHistory.previousMember.id).isEqualTo(previousMember.id)
        assertThat(savedHistory.newMember.id).isEqualTo(newMember.id)
        assertThat(savedHistory.reason).isEqualTo(ShiftAssignmentReason.MANAGER_REASSIGNED)
        assertThat(savedHistory.actorMember?.id).isEqualTo(manager.id)
        assertThat(savedHistory.createDate).isNotNull()
    }

    @Test
    fun 이전_담당자의_이력은_생성_시각이_같으면_ID_내림차순으로_조회한다() {
        val now = LocalDateTime.of(2026, 10, 28, 15, 0)
        val workplace = entityManager.persist(Workplace("SWITCH 카페", "TEST1234"))
        val manager = persistMember(workplace, "manager@test.com", "관리자", WorkplaceRole.MANAGER, now)
        val previousMember = persistMember(workplace, "previous@test.com", "이전 담당자", WorkplaceRole.EMPLOYEE, now)
        val firstNewMember = persistMember(workplace, "first-new@test.com", "첫 새 담당자", WorkplaceRole.EMPLOYEE, now)
        val secondNewMember = persistMember(workplace, "second-new@test.com", "둘째 새 담당자", WorkplaceRole.EMPLOYEE, now)
        val schedule = entityManager.persist(Schedule(workplace, LocalDate.of(2026, 10, 26)))
        val firstShift = entityManager.persist(
            Shift(
                schedule,
                previousMember,
                LocalDateTime.of(2026, 10, 30, 9, 0),
                LocalDateTime.of(2026, 10, 30, 18, 0),
                ShiftStatus.SCHEDULED
            )
        )
        val secondShift = entityManager.persist(
            Shift(
                schedule,
                previousMember,
                LocalDateTime.of(2026, 10, 31, 9, 0),
                LocalDateTime.of(2026, 10, 31, 18, 0),
                ShiftStatus.SCHEDULED
            )
        )
        val firstHistory = entityManager.persist(
            ShiftAssignmentHistory(
                firstShift,
                previousMember,
                firstNewMember,
                ShiftAssignmentReason.MANAGER_REASSIGNED,
                manager
            )
        )
        val secondHistory = entityManager.persist(
            ShiftAssignmentHistory(
                secondShift,
                previousMember,
                secondNewMember,
                ShiftAssignmentReason.MANAGER_REASSIGNED,
                manager
            )
        )
        entityManager.flush()
        entityManager.clear()

        val histories = shiftAssignmentHistoryRepository
            .findAllByPreviousMember_IdOrderByCreateDateDescIdDesc(requireNotNull(previousMember.id))

        assertThat(histories.map { it.id })
            .containsExactly(secondHistory.id, firstHistory.id)
        assertThat(histories.map { it.createDate }.distinct()).hasSize(1)
    }

    private fun persistMember(
        workplace: Workplace,
        email: String,
        name: String,
        role: WorkplaceRole,
        now: LocalDateTime,
    ): WorkplaceMember {
        val user = entityManager.persist(User(email, "password", name))
        return entityManager.persist(WorkplaceMember(workplace, user, role, now.minusDays(1)))
    }
}
