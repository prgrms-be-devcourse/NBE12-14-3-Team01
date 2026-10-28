package com.merge.backend.domain.workplace.repository

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.global.config.TimeConfig
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(TimeConfig::class)
class WorkplaceMemberRepositoryTest {

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    companion object {
        private val JOINED_AT =
            LocalDateTime.of(2026, 9, 1, 9, 0)

        private val LEFT_AT =
            LocalDateTime.of(2026, 9, 15, 18, 0)
    }

    private fun createUser(email: String, name: String): User {
        return entityManager.persist(
            User(email, "test-password", name)
        )
    }

    private fun createWorkplace(name: String, inviteCode: String): Workplace {
        return entityManager.persist(
            Workplace(name, inviteCode)
        )
    }

    private fun createMember(
        workplace: Workplace,
        user: User,
        role: WorkplaceRole,
        leftAt: LocalDateTime?
    ): WorkplaceMember {
        return entityManager.persist(
            WorkplaceMember(
                workplace,
                user,
                role,
                JOINED_AT,
                leftAt
            )
        )
    }

    @Test
    @DisplayName("사용자의 재직 중인 근무지만 조회한다")
    fun findOnlyCurrentWorkplaces() {
        val user = createUser("user1@test.com", "직원1")

        val active1 = createWorkplace("매장A", "TESTA001")
        val active2 = createWorkplace("매장B", "TESTB001")
        val retired = createWorkplace("매장C", "TESTC001")

        createMember(active1, user, WorkplaceRole.EMPLOYEE, null)
        createMember(active2, user, WorkplaceRole.MANAGER, null)
        createMember(retired, user, WorkplaceRole.EMPLOYEE, LEFT_AT)

        entityManager.flush()
        entityManager.clear()

        val result = workplaceMemberRepository
                .findAllByUser_IdAndLeftAtIsNull(user.id)

        assertThat(result.map { member -> member.workplace.id })
            .containsExactlyInAnyOrder(
                active1.id,
                active2.id
            )
    }

    @Test
    @DisplayName("근무지의 현재 재직자만 조회한다")
    fun findOnlyCurrentMembers() {
        val workplace = createWorkplace("매장A", "TESTA002")

        val otherWorkplace = createWorkplace("매장B", "TESTB002")

        val manager = createUser("manager2@test.com", "관리자")
        val employee = createUser("employee2@test.com", "직원")
        val retired = createUser("retired2@test.com", "퇴사자")
        val outsider = createUser("outsider2@test.com", "외부직원")

        createMember(
            workplace, manager, WorkplaceRole.MANAGER, null
        )
        createMember(
            workplace, employee, WorkplaceRole.EMPLOYEE, null
        )
        createMember(
            workplace, retired, WorkplaceRole.EMPLOYEE, LEFT_AT
        )
        createMember(
            otherWorkplace, outsider, WorkplaceRole.EMPLOYEE, null
        )

        entityManager.flush()
        entityManager.clear()

        val result = workplaceMemberRepository
                .findAllByWorkplace_IdAndLeftAtIsNull(
                    workplace.id
                )

        assertThat(result.map { member -> member.user.id })
            .containsExactlyInAnyOrder(
                manager.id,
                employee.id
            )
    }

    @Test
    @DisplayName("현재 재직 중인 근무지의 멤버십만 조회한다")
    fun findActiveMembership() {
        val workplace = createWorkplace("매장A", "TESTA003")

        val otherWorkplace = createWorkplace("매장B", "TESTB003")

        val manager = createUser("manager3@test.com", "관리자")
        val retired = createUser("retired3@test.com", "퇴사자")

        val activeMember = createMember(
            workplace, manager, WorkplaceRole.MANAGER, null
        )

        createMember(
            workplace, retired, WorkplaceRole.MANAGER, LEFT_AT
        )

        entityManager.flush()
        entityManager.clear()

        assertThat(
            workplaceMemberRepository
                .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
                    workplace.id,
                    manager.id
                )
        )
            .isPresent()
            .get()
            .extracting { member: WorkplaceMember -> member.id }
            .isEqualTo(activeMember.id)

        assertThat(
            workplaceMemberRepository
                .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
                    workplace.id,
                    retired.id
                )
        ).isEmpty()

        assertThat(
            workplaceMemberRepository
                .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
                    otherWorkplace.id,
                    manager.id
                )
        ).isEmpty()
    }

    @Test
    @DisplayName("재직 중인 MANAGER만 조회한다")
    fun findOnlyActiveManagers() {
        val workplace = createWorkplace("매장A", "TESTA004")

        val activeManager = createUser("manager4@test.com", "재직관리자")
        val employee = createUser("employee4@test.com", "일반직원")
        val retiredManager = createUser("retired4@test.com", "퇴사관리자")

        createMember(
            workplace, activeManager, WorkplaceRole.MANAGER, null
        )
        createMember(
            workplace, employee, WorkplaceRole.EMPLOYEE, null
        )
        createMember(
            workplace, retiredManager, WorkplaceRole.MANAGER, LEFT_AT
        )

        entityManager.flush()
        entityManager.clear()

        val result = workplaceMemberRepository
                .findAllByWorkplace_IdAndRoleAndLeftAtIsNull(
                    workplace.id,
                    WorkplaceRole.MANAGER
                )

        assertThat(result.map { member -> member.user.id })
            .containsExactly(activeManager.id)
    }

    @Test
    @DisplayName("동일한 사용자와 근무지의 멤버십은 DB에 중복 저장할 수 없다")
    fun rejectDuplicateMembershipAtDatabaseLevel() {
        val user = createUser(
            "duplicate-member@test.com",
            "중복 테스트 직원"
        )

        val workplace = createWorkplace(
            "중복 테스트 매장",
            "DUPL0001"
        )

        // 첫 번째 멤버십 저장
        createMember(
            workplace,
            user,
            WorkplaceRole.EMPLOYEE,
            null
        )

        entityManager.flush()

        // 동일한 사용자 + 근무지 조합으로 두 번째 멤버십 저장
        val duplicateMember = WorkplaceMember(
            workplace,
            user,
            WorkplaceRole.EMPLOYEE,
            JOINED_AT,
            null
        )

        // DB UNIQUE 제약조건에 의해 저장이 거부되어야 한다
        assertThatThrownBy {
            workplaceMemberRepository.saveAndFlush(duplicateMember)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

}
