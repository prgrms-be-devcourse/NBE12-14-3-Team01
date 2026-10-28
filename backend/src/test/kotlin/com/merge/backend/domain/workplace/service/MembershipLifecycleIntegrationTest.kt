package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import com.merge.backend.global.exception.BusinessException
import jakarta.persistence.EntityManager
import java.sql.Connection
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Version
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** 테스트 Transaction으로 감싸지 않고 실제 Service 프록시의 Commit·Rollback을 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class MembershipLifecycleIntegrationTest {
    @Autowired private lateinit var workplaceService: WorkplaceService
    @Autowired private lateinit var historyService: MembershipHistoryService
    @Autowired private lateinit var workplaceRepository: WorkplaceRepository
    @Autowired private lateinit var memberRepository: WorkplaceMemberRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var testClock: TestClock

    private lateinit var fixture: Fixture

    @BeforeEach
    fun setUp() {
        testClock.setInstant(JOIN_INSTANT)
        fixture = checkNotNull(TransactionTemplate(transactionManager).execute {
            val suffix = UUID.randomUUID().toString().replace("-", "")
            val employee = userRepository.save(User("h2-employee-$suffix@test.com", "pw", "직원"))
            val manager = userRepository.save(User("h2-manager-$suffix@test.com", "pw", "관리자"))
            val workplace = workplaceRepository.save(Workplace("H2 통합 매장", suffix.take(8).uppercase()))
            memberRepository.saveAndFlush(
                WorkplaceMember(workplace, manager, WorkplaceRole.MANAGER, LocalDateTime.now(testClock))
            )
            Fixture(
                requireNotNull(workplace.id), requireNotNull(employee.id),
                requireNotNull(manager.id), workplace.inviteCode
            )
        })
    }

    @AfterEach
    fun cleanUp() {
        if (!::fixture.isInitialized) return
        // 이 테스트가 만든 데이터만 FK 순서대로 정리한다. 다른 테스트·실험 자료는 유지한다.
        TransactionTemplate(transactionManager).executeWithoutResult {
            entityManager.createQuery("DELETE FROM WorkplaceMember m WHERE m.workplace.id = :workplaceId")
                .setParameter("workplaceId", fixture.workplaceId)
                .executeUpdate()
            workplaceRepository.deleteById(fixture.workplaceId)
            workplaceRepository.flush()
            userRepository.deleteAllById(listOf(fixture.employeeId, fixture.managerId))
        }
        testClock.setInstant(JOIN_INSTANT)
    }

    @Test
    @DisplayName("실제 가입 Transaction이 현재 소속을 Commit하고 중복 가입은 WPL-002로 거부한다")
    fun joinCommitsCurrentMembershipAndRejectsDuplicate() {
        val joined = join()
        val saved = memberRepository.findById(requireNotNull(joined.id)).orElseThrow()

        assertThat(saved.user.id).isEqualTo(fixture.employeeId)
        assertThat(saved.workplace.id).isEqualTo(fixture.workplaceId)
        assertThat(saved.role).isEqualTo(WorkplaceRole.EMPLOYEE)
        assertThat(saved.joinedAt).isEqualTo(LocalDateTime.now(testClock))
        assertThat(saved.leftAt).isNull()
        assertThat(saved.activeMarker).isEqualTo(1)
        assertThat(currentMember().id).isEqualTo(saved.id)

        val exception = assertThrows<BusinessException> { join() }

        assertThat(exception.errorCode).isSameAs(WorkplaceErrorCode.ALREADY_JOINED_WORKPLACE)
        assertThat(employeeRowCount()).isEqualTo(1L)
        assertThat(currentMember().id).isEqualTo(saved.id)
    }

    @Test
    @DisplayName("퇴사 후 재가입은 새 Row를 생성하고 이전 ID·가입일·퇴사일을 보존한다")
    fun rejoinPreservesEndedMembership() {
        val first = join()
        val firstId = requireNotNull(first.id)
        val firstJoinedAt = first.joinedAt
        val leftAt = LocalDateTime.of(2026, 9, 16, 18, 0)
        endMembershipFixture(firstId, leftAt)

        val ended = memberRepository.findById(firstId).orElseThrow()
        assertThat(ended.activeMarker).isNull()
        assertThat(memberRepository.findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
            fixture.workplaceId, fixture.employeeId
        )).isEmpty()
        assertThat(memberRepository.existsByWorkplaceAndUser(
            workplaceRepository.findById(fixture.workplaceId).orElseThrow(), employee()
        )).isFalse()

        testClock.setInstant(JOIN_INSTANT.plusSeconds(2 * 86_400L))
        val rejoined = join()
        val preserved = memberRepository.findById(firstId).orElseThrow()

        assertThat(rejoined.id).isNotEqualTo(firstId)
        assertThat(rejoined.joinedAt).isEqualTo(LocalDateTime.now(testClock))
        assertThat(rejoined.joinedAt).isAfter(firstJoinedAt)
        assertThat(rejoined.role).isEqualTo(WorkplaceRole.EMPLOYEE)
        assertThat(rejoined.leftAt).isNull()
        assertThat(currentMember().id).isEqualTo(rejoined.id)
        assertThat(preserved.id).isEqualTo(firstId)
        assertThat(preserved.user.id).isEqualTo(fixture.employeeId)
        assertThat(preserved.workplace.id).isEqualTo(fixture.workplaceId)
        assertThat(preserved.joinedAt).isEqualTo(firstJoinedAt)
        assertThat(preserved.leftAt).isEqualTo(leftAt)
        assertThat(preserved.activeMarker).isNull()
        assertThat(employeeRowCount()).isEqualTo(2L)
    }

    @Test
    @DisplayName("종료 이력 두 건과 현재 소속 한 건이 공존하고 이력 조회는 기간별 Row를 유지한다")
    fun multipleEndedMembershipsCoexistWithOneCurrentMembership() {
        val first = join()
        val firstId = requireNotNull(first.id)
        val firstLeftAt = LocalDateTime.of(2026, 9, 16, 18, 0)
        endMembershipFixture(firstId, firstLeftAt)
        testClock.setInstant(JOIN_INSTANT.plusSeconds(2 * 86_400L))
        val second = join()
        val secondId = requireNotNull(second.id)
        val secondLeftAt = LocalDateTime.of(2026, 9, 18, 18, 0)
        endMembershipFixture(secondId, secondLeftAt)
        testClock.setInstant(JOIN_INSTANT.plusSeconds(4 * 86_400L))

        val current = join()
        val history = historyService.getMyEndedMemberships(employee())

        assertThat(setOf(firstId, secondId, current.id)).hasSize(3)
        assertThat(employeeRowCount()).isEqualTo(3L)
        assertThat(currentMember().id).isEqualTo(current.id)
        assertThat(memberRepository.findAllByUser_IdAndLeftAtIsNull(fixture.employeeId))
            .extracting<Long> { requireNotNull(it.id) }.containsExactly(current.id)
        assertThat(history.map { it.memberId }).containsExactly(secondId, firstId)
        assertThat(history.map { it.leftAt }).containsExactly(secondLeftAt, firstLeftAt)
        assertThat(history.map { it.joinedAt }).containsExactly(second.joinedAt, first.joinedAt)
        assertThat(memberRepository.findById(firstId).orElseThrow().activeMarker).isNull()
        assertThat(memberRepository.findById(secondId).orElseThrow().activeMarker).isNull()
        assertThat(currentMember().activeMarker).isEqualTo(1)
    }

    @Test
    @DisplayName("앱이 생성한 MySQL Schema에 생성 컬럼과 이름 붙은 U2 복합 Unique가 적용된다")
    fun mysqlSchemaUsesGeneratedMarkerAndNamedUnique() {
        dataSource.connection.use { connection ->
            assertThat(connection.metaData.databaseProductName).isEqualTo("MySQL")
            assertThat(connection.metaData.databaseProductVersion).startsWith("9.7.2")
            assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
            println("H2 integration: MySQL=${connection.metaData.databaseProductVersion}, " +
                "Hibernate=${Version.getVersionString()}, isolation=${connection.transactionIsolation}")
        }
        val expression = jdbcTemplate.queryForObject(
            """
            SELECT GENERATION_EXPRESSION FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'workplace_member'
                AND COLUMN_NAME = 'active_marker'
            """, String::class.java
        )
        assertThat(requireNotNull(expression).lowercase().replace("`", "").replace(Regex("\\s+"), ""))
            .contains("casewhen(left_atisnull)then1end")

        val uniqueColumns = jdbcTemplate.queryForList(
            """
            SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'workplace_member' AND NON_UNIQUE = 0
            GROUP BY INDEX_NAME
            """, String::class.java
        )
        assertThat(uniqueColumns).contains("workplace_id,user_id,active_marker")
            .doesNotContain("workplace_id,user_id")
        val ddl = jdbcTemplate.queryForMap("SHOW CREATE TABLE workplace_member")["Create Table"].toString()
        assertThat(ddl).contains("GENERATED ALWAYS AS", "STORED",
            "UNIQUE KEY `uk_u2_active_membership` (`workplace_id`,`user_id`,`active_marker`)")
        println("H2 integration DDL:\n$ddl")
    }

    @Test
    @DisplayName("실제 Repository의 현재 소속 중복 INSERT는 이름 붙은 DB Unique 위반으로 롤백된다")
    fun databaseRejectsSecondCurrentMembershipAndPreservesCommittedRow() {
        val first = join()

        val exception = assertThrows<DataIntegrityViolationException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                memberRepository.saveAndFlush(WorkplaceMember(
                    workplaceRepository.findById(fixture.workplaceId).orElseThrow(),
                    employee(), WorkplaceRole.EMPLOYEE, LocalDateTime.now(testClock)
                ))
            }
        }

        val constraint = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<ConstraintViolationException>().single()
        assertThat(constraint.constraintName?.substringAfterLast('.')?.trim('`', '"'))
            .isEqualTo("uk_u2_active_membership")
        assertThat(constraint.sqlException.errorCode).isEqualTo(1062)
        assertThat(constraint.sqlException.sqlState).isEqualTo("23000")
        assertThat(employeeRowCount()).isEqualTo(1L)
        assertThat(currentMember().id).isEqualTo(first.id)
    }

    @Test
    @DisplayName("관리자의 초대 코드 재발급 Commit 후 이전 코드는 WPL-001이고 새 코드로 가입한다")
    fun regeneratedInviteCodeInvalidatesPreviousCode() {
        val regenerated = workplaceService.regenerateInviteCode(fixture.workplaceId, fixture.managerId)
        val newCode = regenerated.inviteCode

        assertThat(newCode).isNotEqualTo(fixture.inviteCode).matches("[0-9A-F]{8}")
        assertThat(workplaceRepository.findIdByInviteCode(fixture.inviteCode)).isNull()
        assertThat(workplaceRepository.findIdByInviteCode(newCode)).isEqualTo(fixture.workplaceId)
        assertThat(workplaceService.getInviteCode(fixture.workplaceId, fixture.managerId).inviteCode)
            .isEqualTo(newCode)
        val exception = assertThrows<BusinessException> { join() }
        assertThat(exception.errorCode).isSameAs(WorkplaceErrorCode.INVALID_INVITE_CODE)
        assertThat(employeeRowCount()).isZero()

        val joined = workplaceService.joinWorkplace(newCode, employee())
        assertThat(currentMember().id).isEqualTo(joined.id)
        assertThat(employeeRowCount()).isEqualTo(1L)
    }

    private fun employee(): User = userRepository.findById(fixture.employeeId).orElseThrow()

    private fun join(): WorkplaceMember = workplaceService.joinWorkplace(fixture.inviteCode, employee())

    private fun currentMember(): WorkplaceMember = memberRepository
        .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(fixture.workplaceId, fixture.employeeId).orElseThrow()

    private fun employeeRowCount(): Long = requireNotNull(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM workplace_member WHERE workplace_id = ? AND user_id = ?",
        Long::class.javaObjectType, fixture.workplaceId, fixture.employeeId
    ))

    private fun endMembershipFixture(memberId: Long, leftAt: LocalDateTime) {
        // PR 1은 퇴사 메서드에 의존하지 않는다. 근무·대타가 없는 Fixture의 종료 상태만 준비한다.
        TransactionTemplate(transactionManager).executeWithoutResult {
            val member = memberRepository.findById(memberId).orElseThrow()
            ReflectionTestUtils.setField(member, "leftAt", leftAt)
            memberRepository.flush()
        }
    }

    private data class Fixture(
        val workplaceId: Long,
        val employeeId: Long,
        val managerId: Long,
        val inviteCode: String
    )

    companion object {
        private val JOIN_INSTANT = Instant.parse("2026-09-15T01:00:00Z")
    }
}
