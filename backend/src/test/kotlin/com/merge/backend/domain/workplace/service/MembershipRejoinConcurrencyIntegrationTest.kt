package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.exception.BusinessException
import com.zaxxer.hikari.HikariDataSource
import java.time.Clock
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// Same Context configuration as the existing membership integration tests: no extra Hikari Pool.
// No test-wide transaction: worker transactions must commit independently.
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Timeout(90)
class MembershipRejoinConcurrencyIntegrationTest {
    @Autowired private lateinit var workplaceService: WorkplaceService
    @Autowired private lateinit var workplaceRepository: WorkplaceRepository
    @Autowired private lateinit var memberRepository: WorkplaceMemberRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var clock: Clock

    private data class Fixture(
        val workplaceId: Long,
        val managerId: Long,
        val employeeId: Long,
        val endedId: Long,
        val oldCode: String,
        val joinedAt: LocalDateTime,
        val leftAt: LocalDateTime
    )

    private data class JoinOutcome(val memberId: Long?, val error: WorkplaceErrorCode?)
    private lateinit var fixture: Fixture

    private fun <T : Any> committed(block: () -> T): T =
        requireNotNull(TransactionTemplate(transactionManager).execute { block() })

    @BeforeEach
    fun createCommittedFixture() {
        val pool = dataSource.unwrap(HikariDataSource::class.java)
        assertThat(pool.maximumPoolSize).isGreaterThanOrEqualTo(THREAD_COUNT)
        assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String::class.java))
            .isEqualTo("READ-COMMITTED")
        val serverLimit = jdbc.queryForObject("SELECT @@max_connections", Int::class.java)
        println("Membership concurrency: pool=${pool.poolName}, poolSize=${pool.maximumPoolSize}, maxConnections=$serverLimit")
        fixture = committed {
            val key = UUID.randomUUID().toString()
            val manager = userRepository.save(User("manager-$key@concurrency.test", "test-password", "manager"))
            val employee = userRepository.save(User("employee-$key@concurrency.test", "test-password", "employee"))
            val workplace = workplaceService.createWorkplace("concurrency-$key", manager).workplace
            val joinedAt = LocalDateTime.now(clock).minusDays(20).withNano(0)
            val leftAt = joinedAt.plusDays(10)
            val ended = memberRepository.saveAndFlush(
                WorkplaceMember(workplace, employee, WorkplaceRole.EMPLOYEE, joinedAt, leftAt))
            Fixture(requireNotNull(workplace.id), requireNotNull(manager.id), requireNotNull(employee.id),
                requireNotNull(ended.id), workplace.inviteCode, joinedAt, leftAt)
        }
    }

    @AfterEach
    fun removeOnlyThisTestsCommittedFixture() {
        if (!::fixture.isInitialized) return
        committed {
            // 가입·재가입 알림(WORKPLACE_MEMBER_JOINED)이 관리자 소속을 참조하므로 먼저 지운다
            jdbc.update(
                "DELETE FROM notification WHERE recipient_member_id IN (SELECT id FROM workplace_member WHERE workplace_id = ?)",
                fixture.workplaceId,
            )
            jdbc.update("DELETE FROM workplace_member WHERE workplace_id = ?", fixture.workplaceId)
            jdbc.update("DELETE FROM workplace WHERE id = ?", fixture.workplaceId)
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", fixture.managerId, fixture.employeeId)
        }
    }

    private fun joinUsing(code: String): Long {
        val employee = userRepository.findById(fixture.employeeId).orElseThrow()
        return requireNotNull(workplaceService.joinWorkplace(code, employee).id)
    }

    private fun joinOutcome(code: String): JoinOutcome = try {
        JoinOutcome(joinUsing(code), null)
    } catch (exception: BusinessException) {
        // Unexpected exceptions escape through Future.get; only the expected business errors are collected.
        val codeValue = exception.errorCode
        require(codeValue is WorkplaceErrorCode) { "Unexpected join error: $codeValue" }
        JoinOutcome(null, codeValue)
    }

    private fun awaitWorkplaceLockWait() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            // MySQL exposes this user's own sessions without global PROCESS privileges.
            // The holder has finished its SELECT and is waiting on a latch; this active FOR UPDATE is the waiter.
            val statements = jdbc.query("SHOW FULL PROCESSLIST") { row, _ -> row.getString("Info") ?: "" }
            if (statements.any {
                    val sql = it.lowercase(Locale.ROOT)
                    sql.contains("workplace") && sql.contains("for update")
                }) return
            Thread.sleep(20)
        }
        error("The competing transaction did not reach the Workplace FOR UPDATE query")
    }

    private fun stopWorkers(executor: ExecutorService) {
        executor.shutdownNow()
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue()
    }

    private fun assertOneCurrentAndOriginalHistory() {
        committed {
            val ended = memberRepository.findById(fixture.endedId).orElseThrow()
            assertThat(ended.joinedAt).isEqualTo(fixture.joinedAt)
            assertThat(ended.leftAt).isEqualTo(fixture.leftAt)
            assertThat(ended.role).isEqualTo(WorkplaceRole.EMPLOYEE)
            val current = memberRepository.findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
                fixture.workplaceId, fixture.employeeId).orElseThrow()
            assertThat(current.id).isNotEqualTo(fixture.endedId)
            assertThat(current.role).isEqualTo(WorkplaceRole.EMPLOYEE)
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workplace_member WHERE workplace_id=? AND user_id=? AND left_at IS NULL",
                Int::class.java, fixture.workplaceId, fixture.employeeId)).isEqualTo(1)
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workplace_member WHERE workplace_id=? AND user_id=?",
                Int::class.java, fixture.workplaceId, fixture.employeeId)).isEqualTo(2)
        }
    }

    @RepeatedTest(3)
    fun concurrentRejoinsCommitOneNewMembershipAndRejectNineDuplicates() {
        val executor = Executors.newFixedThreadPool(THREAD_COUNT)
        val ready = CountDownLatch(THREAD_COUNT)
        val start = CountDownLatch(1)
        try {
            val futures = List(THREAD_COUNT) {
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(30, TimeUnit.SECONDS))
                    joinOutcome(fixture.oldCode)
                })
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            val outcomes = futures.map { it.get(40, TimeUnit.SECONDS) }
            assertThat(outcomes.filter { it.memberId != null }).hasSize(1)
            assertThat(outcomes.filter { it.memberId == null }).hasSize(THREAD_COUNT - 1)
                .allSatisfy { assertThat(it.error).isEqualTo(WorkplaceErrorCode.ALREADY_JOINED_WORKPLACE) }
            assertOneCurrentAndOriginalHistory()
        } finally {
            start.countDown()
            stopWorkers(executor)
        }
    }

    @RepeatedTest(3)
    fun regenerationCommitsBeforeWaitingJoinSoOldCodeIsRejectedAndNewCodeWorks() {
        val executor = Executors.newFixedThreadPool(2)
        val regeneratedUnderLock = CountDownLatch(1)
        val allowRegenerationCommit = CountDownLatch(1)
        try {
            val regeneration = executor.submit(Callable {
                committed {
                    val newCode = workplaceService.regenerateInviteCode(fixture.workplaceId, fixture.managerId).inviteCode
                    regeneratedUnderLock.countDown()
                    check(allowRegenerationCommit.await(30, TimeUnit.SECONDS))
                    newCode
                }
            })
            assertThat(regeneratedUnderLock.await(15, TimeUnit.SECONDS)).isTrue()
            val joining = executor.submit(Callable { joinOutcome(fixture.oldCode) })
            awaitWorkplaceLockWait()
            assertThat(joining.isDone).isFalse()
            allowRegenerationCommit.countDown()
            val newCode = regeneration.get(40, TimeUnit.SECONDS)
            val outcome = joining.get(40, TimeUnit.SECONDS)
            assertThat(newCode).isNotEqualTo(fixture.oldCode)
            assertThat(outcome.memberId).isNull()
            assertThat(outcome.error).isEqualTo(WorkplaceErrorCode.INVALID_INVITE_CODE)
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workplace_member WHERE workplace_id=? AND user_id=? AND left_at IS NULL",
                Int::class.java, fixture.workplaceId, fixture.employeeId)).isZero()
            assertThat(joinUsing(newCode)).isNotEqualTo(fixture.endedId)
            assertOneCurrentAndOriginalHistory()
        } finally {
            allowRegenerationCommit.countDown()
            stopWorkers(executor)
        }
    }

    @RepeatedTest(3)
    fun joinCommitsBeforeWaitingRegenerationAndOldCodeIsInvalidAfterward() {
        val executor = Executors.newFixedThreadPool(2)
        val joinedUnderLock = CountDownLatch(1)
        val allowJoinCommit = CountDownLatch(1)
        try {
            val joining = executor.submit(Callable {
                committed {
                    val memberId = joinUsing(fixture.oldCode)
                    joinedUnderLock.countDown()
                    check(allowJoinCommit.await(30, TimeUnit.SECONDS))
                    memberId
                }
            })
            assertThat(joinedUnderLock.await(15, TimeUnit.SECONDS)).isTrue()
            val regeneration = executor.submit(Callable {
                workplaceService.regenerateInviteCode(fixture.workplaceId, fixture.managerId).inviteCode
            })
            awaitWorkplaceLockWait()
            assertThat(regeneration.isDone).isFalse()
            allowJoinCommit.countDown()
            assertThat(joining.get(40, TimeUnit.SECONDS)).isNotEqualTo(fixture.endedId)
            val newCode = regeneration.get(40, TimeUnit.SECONDS)
            assertThat(newCode).isNotEqualTo(fixture.oldCode)
            val staleCodeResult = joinOutcome(fixture.oldCode)
            assertThat(staleCodeResult.memberId).isNull()
            assertThat(staleCodeResult.error).isEqualTo(WorkplaceErrorCode.INVALID_INVITE_CODE)
            assertOneCurrentAndOriginalHistory()
            assertThat(workplaceRepository.findById(fixture.workplaceId).orElseThrow().inviteCode).isEqualTo(newCode)
        } finally {
            allowJoinCommit.countDown()
            stopWorkers(executor)
        }
    }

    companion object {
        private const val THREAD_COUNT = 10
    }
}
