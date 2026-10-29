package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.dto.ShiftReassignRequest
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import com.merge.backend.global.exception.BusinessException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// ML-3 H6-C4 / ML-H2 V3-N-17: 관리자 두 명이 같은 공개 근무의 담당자를 동시에 바꾼다 (실제 MySQL Row Lock 대기)
// 첫 재배정은 바깥 Transaction 안에서 끝낸 뒤 Commit 전까지 Lock을 쥐고, 두 번째 재배정이 Schedule FOR UPDATE에서 기다리는지 확인한다
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
@Timeout(60)
class PublishedReassignConcurrencyTest {

    @Autowired
    private lateinit var shiftService: ShiftService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var testClock: TestClock

    private val executor = Executors.newFixedThreadPool(2)

    @BeforeEach
    fun fixClock() {
        testClock.setInstant(FIXED_NOW)
    }

    @AfterEach
    fun shutdown() {
        executor.shutdownNow()
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    @DisplayName("H6-C4: 서로 다른 대상으로 동시에 재배정하면 뒤 요청은 앞 Commit을 기다린 뒤 최신 담당자에서 이어 받아, 최종 담당자 1명·배정 이력 2건이 반영 순서대로 이어진다")
    fun concurrentReassignmentsToDifferentTargetsAreSerializedInHistoryOrder() {
        // given
        val f = createFixture()

        // when: M1이 T1으로 재배정한 채 Commit을 미루는 동안 M2가 T2로 재배정
        val second = race(
            first = { reassign(f, f.target1, f.manager1) },
            second = { reassign(f, f.target2, f.manager2) },
        )
        second.get(15, TimeUnit.SECONDS)

        // then: 최종 담당자는 나중에 반영된 T2, 이력은 A→T1(M1), T1→T2(M2)로 끊김 없이 이어진다
        assertThat(memberOf(f.shiftId)).isEqualTo(f.target2.memberId)
        assertThat(historyOf(f.shiftId)).containsExactly(
            listOf(f.owner.memberId, f.target1.memberId, f.manager1.memberId),
            listOf(f.target1.memberId, f.target2.memberId, f.manager2.memberId),
        )
    }

    @Test
    @DisplayName("H6-C4: 같은 대상으로 동시에 재배정하면 뒤 요청은 Lock 뒤 최신 담당자를 읽어 SFT 같은 담당자 오류로 거부되고, 배정 이력은 1건이다")
    fun concurrentReassignmentsToSameTargetLeaveOneHistory() {
        // given
        val f = createFixture()

        // when
        val second = race(
            first = { reassign(f, f.target1, f.manager1) },
            second = { reassign(f, f.target1, f.manager2) },
        )

        // then
        val error = assertThrows<java.util.concurrent.ExecutionException> { second.get(15, TimeUnit.SECONDS) }.cause
        assertThat((error as BusinessException).errorCode).isEqualTo(ShiftErrorCode.SAME_SHIFT_MEMBER_REASSIGNMENT)
        assertThat(memberOf(f.shiftId)).isEqualTo(f.target1.memberId)
        assertThat(historyOf(f.shiftId)).containsExactly(listOf(f.owner.memberId, f.target1.memberId, f.manager1.memberId))
    }

    // ===================== 헬퍼 =====================

    // first를 바깥 Transaction 안에서 끝내고 Lock을 쥔 채, second가 실제로 Schedule Row Lock을 기다리는지 확인한 뒤 Commit한다
    private fun race(first: () -> Unit, second: () -> Unit): Future<*> {
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = executor.submit {
            TransactionTemplate(transactionManager).executeWithoutResult {
                first()
                held.countDown()
                check(release.await(15, TimeUnit.SECONDS))
            }
        }
        check(held.await(15, TimeUnit.SECONDS))
        val waiter = executor.submit { second() }
        try {
            assertThrows<TimeoutException> { waiter.get(250, TimeUnit.MILLISECONDS) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var waiting = false
            while (System.nanoTime() < deadline) {
                // test 계정의 자기 Session만 조회한다
                val statements = jdbcTemplate.query("SHOW FULL PROCESSLIST") { row, _ -> row.getString("Info")?.lowercase().orEmpty() }
                if (statements.any { it.contains("from schedule ") && it.contains("for update") }) {
                    waiting = true
                    break
                }
                Thread.sleep(25)
            }
            assertThat(waiting).describedAs("실제 MySQL Schedule Row Lock 대기").isTrue()
        } finally {
            release.countDown()
        }
        holder.get(15, TimeUnit.SECONDS)
        return waiter
    }

    private fun reassign(f: Fixture, target: MemberRef, actor: MemberRef) {
        shiftService.reassignPublishedShift(f.workplaceId, f.scheduleId, f.shiftId, ShiftReassignRequest(target.memberId, emptyList()), actor.userId)
    }

    private fun memberOf(shiftId: Long): Long? =
        jdbcTemplate.queryForObject("SELECT member_id FROM shift WHERE id = ?", Long::class.java, shiftId)

    // (이전 담당, 새 담당, 실행자) — 저장 순서(id)대로
    private fun historyOf(shiftId: Long): List<List<Long>> =
        jdbcTemplate.query(
            "SELECT previous_member_id, new_member_id, actor_member_id FROM shift_assignment_history WHERE shift_id = ? ORDER BY id",
            { row, _ -> listOf(row.getLong(1), row.getLong(2), row.getLong(3)) },
            shiftId,
        )

    private data class MemberRef(val memberId: Long, val userId: Long)

    private data class Fixture(
        val workplaceId: Long,
        val scheduleId: Long,
        val shiftId: Long,
        val manager1: MemberRef,
        val manager2: MemberRef,
        val owner: MemberRef,
        val target1: MemberRef,
        val target2: MemberRef,
    )

    // 관리자 M1·M2, 담당자 A의 공개 근무 1개, 재배정 대상 T1·T2
    private fun createFixture(): Fixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val suffix = UUID.randomUUID().toString().substring(0, 8)
            val workplace = workplaceRepository.save(Workplace("동시 재배정 테스트 $suffix", "RAS-$suffix"))
            val startAt = now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)
            val schedule = Schedule(workplace, startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                .also { it.publish(now()) }
                .let { scheduleRepository.save(it) }
            fun member(name: String, role: WorkplaceRole): WorkplaceMember {
                val user = userRepository.save(User("$name-$suffix@test.com", "password-hash", name))
                return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, now().minusDays(30)))
            }
            fun ref(member: WorkplaceMember) = MemberRef(requireNotNull(member.id), requireNotNull(member.user.id))
            val m1 = member("manager1", WorkplaceRole.MANAGER)
            val m2 = member("manager2", WorkplaceRole.MANAGER)
            val owner = member("owner", WorkplaceRole.EMPLOYEE)
            val t1 = member("target1", WorkplaceRole.EMPLOYEE)
            val t2 = member("target2", WorkplaceRole.EMPLOYEE)
            val shift = shiftRepository.save(Shift(schedule, owner, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))
            Fixture(
                workplaceId = requireNotNull(workplace.id),
                scheduleId = requireNotNull(schedule.id),
                shiftId = requireNotNull(shift.id),
                manager1 = ref(m1),
                manager2 = ref(m2),
                owner = ref(owner),
                target1 = ref(t1),
                target2 = ref(t2),
            )
        },
    )

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    companion object {
        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")
    }
}
