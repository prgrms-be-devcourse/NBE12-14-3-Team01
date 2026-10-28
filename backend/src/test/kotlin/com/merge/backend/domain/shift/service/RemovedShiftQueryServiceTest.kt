package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.dto.RemovedShiftEvent
import com.merge.backend.domain.shift.dto.RemovedShiftReason
import com.merge.backend.domain.shift.dto.RemovedShiftResponse
import com.merge.backend.domain.shift.dto.ShiftReassignRequest
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// 빠진 근무 조회 (API-SC-01). 취소·담당자 변경은 실제 공개 후 변경 Command로 만들고 MySQL 결과를 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class RemovedShiftQueryServiceTest {

    @Autowired
    private lateinit var removedShiftQueryService: RemovedShiftQueryService

    @Autowired
    private lateinit var shiftService: ShiftService

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var testClock: TestClock

    @BeforeEach
    fun fixClock() {
        // 같은 Context를 쓰는 다른 테스트가 시간을 바꿨을 수 있으므로 매번 고정
        testClock.setInstant(FIXED_NOW)
    }

    @Test
    @DisplayName("관리자가 취소한 내 근무는 CANCELLED / MANAGER_CANCELLED로 보이고 사건 시각은 취소 시각이다")
    fun cancelledShiftIsListed() {
        // given
        val f = createFixture()
        val shift = createShift(f, f.employeeA, daysFromNow = 3)
        moveClock(hours = 1)
        val cancelledAt = now()
        cancel(f, shift)

        // when
        val responses = removedShiftsOf(f.employeeA)

        // then
        assertThat(responses).hasSize(1)
        val response = responses[0]
        assertThat(response.shiftId).isEqualTo(shift.id)
        assertThat(response.workplaceId).isEqualTo(f.workplace.id)
        assertThat(response.workplaceName).isEqualTo(f.workplace.name)
        assertThat(response.startAt).isEqualTo(shift.startAt)
        assertThat(response.endAt).isEqualTo(shift.endAt)
        assertThat(response.event).isEqualTo(RemovedShiftEvent.CANCELLED)
        assertThat(response.reason).isEqualTo(RemovedShiftReason.MANAGER_CANCELLED)
        assertThat(response.occurredAt).isEqualTo(cancelledAt)
        assertThat(removedShiftsOf(f.employeeB)).isEmpty()
    }

    @Test
    @DisplayName("다른 직원에게 넘어간 내 근무는 REASSIGNED / MANAGER_REASSIGNED로 보이고, 새 담당에게는 빠진 근무가 아니다")
    fun reassignedShiftIsListedForPreviousMemberOnly() {
        // given
        val f = createFixture()
        val shift = createShift(f, f.employeeA, daysFromNow = 3)
        moveClock(hours = 2)
        val reassignedAt = now()
        reassign(f, shift, f.employeeB)

        // when
        val responses = removedShiftsOf(f.employeeA)

        // then
        assertThat(responses).hasSize(1)
        assertThat(responses[0].shiftId).isEqualTo(shift.id)
        assertThat(responses[0].event).isEqualTo(RemovedShiftEvent.REASSIGNED)
        assertThat(responses[0].reason).isEqualTo(RemovedShiftReason.MANAGER_REASSIGNED)
        assertThat(responses[0].occurredAt).isEqualTo(reassignedAt)
        assertThat(removedShiftsOf(f.employeeB)).isEmpty()
    }

    @Test
    @DisplayName("응답에는 새 담당자의 이름·ID가 없다")
    fun responseHasNoNewAssigneeInformation() {
        val fieldNames = RemovedShiftResponse::class.java.recordComponents.map { it.name }

        assertThat(fieldNames).containsExactly(
            "shiftId", "workplaceId", "workplaceName", "startAt", "endAt", "event", "occurredAt", "reason",
        )
    }

    @Test
    @DisplayName("A -> B -> A로 돌아온 근무는 A의 빠진 근무가 아니고, B에게는 빠진 근무로 보인다")
    fun shiftReturnedToMeIsExcluded() {
        // given
        val f = createFixture()
        val shift = createShift(f, f.employeeA, daysFromNow = 3)
        reassign(f, shift, f.employeeB)
        moveClock(hours = 1)
        val returnedAt = now()
        reassign(f, shift, f.employeeA)

        // when & then
        assertThat(removedShiftsOf(f.employeeA)).isEmpty()
        val responsesOfB = removedShiftsOf(f.employeeB)
        assertThat(responsesOfB).hasSize(1)
        assertThat(responsesOfB[0].occurredAt).isEqualTo(returnedAt)
    }

    @Test
    @DisplayName("V2-N-39: A -> B -> A -> 취소면 A에게는 취소됨 한 줄만(인계됨 중복 없음), B에게는 인계됨 한 줄. A -> B -> C면 A·B 모두 인계됨")
    fun v2N39QueryRules() {
        // given: A -> B -> A -> 취소
        val f = createFixture()
        val returnedThenCancelled = createShift(f, f.employeeA, daysFromNow = 3)
        reassign(f, returnedThenCancelled, f.employeeB)
        reassign(f, returnedThenCancelled, f.employeeA)
        cancel(f, returnedThenCancelled)

        // given: A -> B -> C (다른 근무지에서 따로 확인)
        val g = createFixture()
        val employeeC = createEmployee(g.workplace, "직원C")
        val passedOn = createShift(g, g.employeeA, daysFromNow = 3)
        reassign(g, passedOn, g.employeeB)
        reassign(g, passedOn, employeeC)

        // then
        assertThat(removedShiftsOf(f.employeeA).map { Pair<Long?, RemovedShiftEvent>(it.shiftId, it.event) })
            .containsExactly(returnedThenCancelled.id to RemovedShiftEvent.CANCELLED)
        assertThat(removedShiftsOf(f.employeeB).map { Pair<Long?, RemovedShiftEvent>(it.shiftId, it.event) })
            .containsExactly(returnedThenCancelled.id to RemovedShiftEvent.REASSIGNED)
        assertThat(removedShiftsOf(g.employeeA).map { Pair<Long?, RemovedShiftEvent>(it.shiftId, it.event) })
            .containsExactly(passedOn.id to RemovedShiftEvent.REASSIGNED)
        assertThat(removedShiftsOf(g.employeeB).map { Pair<Long?, RemovedShiftEvent>(it.shiftId, it.event) })
            .containsExactly(passedOn.id to RemovedShiftEvent.REASSIGNED)
        assertThat(removedShiftsOf(employeeC)).isEmpty()
    }

    @Test
    @DisplayName("같은 근무에서 여러 번 이전 담당이었으면 마지막 변경 한 줄만 보인다 (A -> B -> A -> C)")
    fun onlyLatestHistoryRowIsListed() {
        // given
        val f = createFixture()
        val employeeC = createEmployee(f.workplace, "직원C")
        val shift = createShift(f, f.employeeA, daysFromNow = 3)
        reassign(f, shift, f.employeeB)
        moveClock(hours = 1)
        reassign(f, shift, f.employeeA)
        moveClock(hours = 1)
        val lastReassignedAt = now()
        reassign(f, shift, employeeC)

        // when
        val responses = removedShiftsOf(f.employeeA)

        // then
        assertThat(responses).hasSize(1)
        assertThat(responses[0].occurredAt).isEqualTo(lastReassignedAt)
        assertThat(removedShiftsOf(f.employeeB)).hasSize(1)
        assertThat(removedShiftsOf(employeeC)).isEmpty()
    }

    @Test
    @DisplayName("넘어간 근무가 새 담당에게서 취소되면 이전 담당에게는 REASSIGNED로만 보인다")
    fun reassignedThenCancelledShiftShowsReassignedForPreviousMember() {
        // given
        val f = createFixture()
        val shift = createShift(f, f.employeeA, daysFromNow = 3)
        reassign(f, shift, f.employeeB)
        moveClock(hours = 1)
        cancel(f, shift)

        // when & then
        val responsesOfA = removedShiftsOf(f.employeeA)
        assertThat(responsesOfA).hasSize(1)
        assertThat(responsesOfA[0].event).isEqualTo(RemovedShiftEvent.REASSIGNED)
        val responsesOfB = removedShiftsOf(f.employeeB)
        assertThat(responsesOfB).hasSize(1)
        assertThat(responsesOfB[0].event).isEqualTo(RemovedShiftEvent.CANCELLED)
    }

    @Test
    @DisplayName("원래 시작 시각이 정확히 7일 전인 근무는 보이고, 그보다 1분 이른 근무는 보이지 않는다")
    fun sevenDayBoundaryIsInclusive() {
        // given: 근무 시작 전에 취소해 두고, 시간을 근무 시작 7일 뒤로 옮김
        val f = createFixture()
        val boundaryStart = now().plusDays(1)
        val included = createShiftAt(f, f.employeeA, boundaryStart)
        val excluded = createShiftAt(f, f.employeeA, boundaryStart.minusMinutes(1))
        cancel(f, included)
        cancel(f, excluded)
        testClock.setInstant(FIXED_NOW.plusSeconds(8 * 86_400))

        // when
        val responses = removedShiftsOf(f.employeeA)

        // then
        assertThat(responses.map { it.shiftId }).containsExactly(included.id)
    }

    @Test
    @DisplayName("퇴사 후 재가입하면 이전 소속 기간에 빠진 근무는 보이지 않는다")
    fun previousMembershipIsExcludedAfterRejoin() {
        // given
        val f = createFixture()
        val cancelled = createShift(f, f.employeeA, daysFromNow = 3)
        val reassigned = createShift(f, f.employeeA, daysFromNow = 4)
        cancel(f, cancelled)
        reassign(f, reassigned, f.employeeB)
        moveClock(hours = 1)
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), f.employeeA.id)
        workplaceMemberRepository.save(WorkplaceMember(f.workplace, f.employeeA.user, WorkplaceRole.EMPLOYEE, now()))

        // when & then
        assertThat(removedShiftsOf(f.employeeA)).isEmpty()
    }

    @Test
    @DisplayName("배정 이력 없이 담당이 바뀐 근무(대타 승인)는 빠진 근무가 아니다")
    fun memberChangeWithoutHistoryIsExcluded() {
        // given: 대타 승인은 Shift.changeMember만 하고 배정 이력을 남기지 않는다
        val f = createFixture()
        val shift = createShift(f, f.employeeA, daysFromNow = 3)
        TransactionTemplate(transactionManager).executeWithoutResult {
            shiftRepository.findById(requireNotNull(shift.id)).orElseThrow().changeMember(
                workplaceMemberRepository.findById(requireNotNull(f.employeeB.id)).orElseThrow(),
            )
        }

        // when & then
        assertThat(removedShiftsOf(f.employeeA)).isEmpty()
    }

    @Test
    @DisplayName("여러 근무지의 빠진 근무를 사건 시각 최신순, 같으면 근무 ID 최신순으로 보여 준다")
    fun responsesAreSortedByOccurredAtDescending() {
        // given
        val f = createFixture()
        val other = createFixture()
        val otherMemberOfA = workplaceMemberRepository.save(
            WorkplaceMember(other.workplace, f.employeeA.user, WorkplaceRole.EMPLOYEE, now()),
        )
        val first = createShift(f, f.employeeA, daysFromNow = 5)
        val second = createShift(other, otherMemberOfA, daysFromNow = 2)
        val sameTime1 = createShift(f, f.employeeA, daysFromNow = 3)
        val sameTime2 = createShift(f, f.employeeA, daysFromNow = 4)
        cancel(f, first)
        moveClock(hours = 1)
        reassign(other, second, other.employeeB)
        moveClock(hours = 1)
        cancel(f, sameTime1)
        cancel(f, sameTime2)

        // when
        val responses = removedShiftsOf(f.employeeA)

        // then
        assertThat(responses.map { it.shiftId })
            .containsExactly(sameTime2.id, sameTime1.id, second.id, first.id)
        assertThat(responses.map { it.workplaceId }.toSet()).containsExactlyInAnyOrder(f.workplace.id, other.workplace.id)
    }

    @Test
    @DisplayName("조회해도 근무·배정 이력은 바뀌지 않는다")
    fun queryDoesNotChangeState() {
        // given
        val f = createFixture()
        val shift = createShift(f, f.employeeA, daysFromNow = 3)
        reassign(f, shift, f.employeeB)
        val before = snapshot(shift)

        // when
        removedShiftsOf(f.employeeA)

        // then
        assertThat(snapshot(shift)).isEqualTo(before)
    }

    private fun removedShiftsOf(member: WorkplaceMember): List<RemovedShiftResponse> =
        removedShiftQueryService.findRemovedShifts(requireNotNull(member.user.id))

    private fun cancel(f: Fixture, shift: Shift) {
        shiftService.cancelPublishedShift(f.workplace.id, f.schedule.id, shift.id, f.manager.id)
    }

    private fun reassign(f: Fixture, shift: Shift, target: WorkplaceMember) {
        shiftService.reassignPublishedShift(
            f.workplace.id,
            f.schedule.id,
            shift.id,
            ShiftReassignRequest(target.id, emptyList()),
            f.manager.id,
        )
    }

    private fun snapshot(shift: Shift): List<Map<String, Any?>> =
        jdbcTemplate.queryForList("SELECT member_id, status FROM shift WHERE id = ?", shift.id) +
            jdbcTemplate.queryForList(
                "SELECT id, previous_member_id, new_member_id FROM shift_assignment_history WHERE shift_id = ? ORDER BY id",
                shift.id,
            )

    private fun createFixture(): Fixture {
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        val manager = userRepository.save(User("manager-$suffix@test.com", "password", "관리자"))
        val workplace = workplaceRepository.save(Workplace("근무지-$suffix", "code-$suffix"))
        workplaceMemberRepository.save(WorkplaceMember(workplace, manager, WorkplaceRole.MANAGER, now()))
        val employeeA = createEmployee(workplace, "직원A")
        val employeeB = createEmployee(workplace, "직원B")
        // 근무가 여러 주에 걸칠 수 있어 근무표 주는 근무마다 맞추지 않고, 공개된 근무표 하나에 시간을 그대로 둔다
        val schedule = Schedule(workplace, LocalDate.of(2026, 9, 14))
        schedule.publish(now())

        return Fixture(manager, workplace, scheduleRepository.save(schedule), employeeA, employeeB)
    }

    private fun createEmployee(workplace: Workplace, name: String): WorkplaceMember {
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        val user = userRepository.save(User("$name-$suffix@test.com", "password", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, WorkplaceRole.EMPLOYEE, now()))
    }

    private fun createShift(f: Fixture, member: WorkplaceMember, daysFromNow: Long): Shift =
        createShiftAt(f, member, now().plusDays(daysFromNow))

    private fun createShiftAt(f: Fixture, member: WorkplaceMember, startAt: LocalDateTime): Shift =
        shiftRepository.save(Shift(f.schedule, member, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    private fun moveClock(hours: Long) {
        testClock.setInstant(testClock.instant().plusSeconds(hours * 3_600))
    }

    private class Fixture(
        val manager: User,
        val workplace: Workplace,
        val schedule: Schedule,
        val employeeA: WorkplaceMember,
        val employeeB: WorkplaceMember,
    )

    companion object {

        // 2026-09-15 10:00 KST
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")
    }
}
