package com.merge.backend.domain.workplace.repository

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.global.config.TimeConfig
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.annotation.DirtiesContext

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LeavePreviewQueryRepositoryTest {
    @Autowired private lateinit var repository: LeavePreviewQueryRepository
    @Autowired private lateinit var entityManager: TestEntityManager

    private val now = LocalDateTime.of(2026, 10, 28, 12, 0)
    private lateinit var workplace: Workplace
    private lateinit var target: WorkplaceMember
    private lateinit var other: WorkplaceMember
    private lateinit var published: Schedule
    private var sequence = 0

    @BeforeEach
    fun setUp() {
        workplace = persist(Workplace("미리보기 매장", "PREVIEW1"))
        target = member()
        other = member()
        published = persist(Schedule(workplace, LocalDate.of(2026, 10, 26)).apply {
            publish(now.minusDays(1))
        })
    }

    @Test
    fun selectOngoingFutureAndAllDraftShiftsAtTimeBoundaries() {
        val startingNow = shift(start = now)
        val ongoing = shift(start = now.minusHours(1))
        val future = shift()
        shift(start = now.minusHours(4)) // endAt == now
        shift(status = ShiftStatus.CANCELLED)
        shift(assigned = other)
        val draft = persist(Schedule(workplace, LocalDate.of(2026, 11, 2)))
        val oldDraftShift = shift(schedule = draft, start = now.minusDays(3))
        val futureDraftShift = shift(schedule = draft, start = now.plusDays(8))
        flushAndClear()

        val shifts = repository.findAffectedShifts(workplaceId(), targetId(), now)

        assertThat(shifts.map { it.id }).containsExactly(
            oldDraftShift.id, ongoing.id, startingNow.id, future.id, futureDraftShift.id
        )
    }

    @Test
    fun selectOnlyTargetAssignmentsOrActiveCandidaciesInThisWorkplace() {
        val own = request(shift())
        val candidateRequest = request(shift(assigned = other))
        persist(SubstituteCandidate(candidateRequest, target))
        val accepted = request(shift(assigned = other))
        persist(SubstituteCandidate(accepted, target)).acceptRequest(now)
        val rejected = request(shift(assigned = other))
        persist(SubstituteCandidate(rejected, target)).reject(now)
        request(shift(assigned = other))
        request(shift(start = now))
        request(shift(status = ShiftStatus.CANCELLED))
        request(shift(), RequestStatus.CLOSED)
        request(shift(), RequestStatus.APPROVED)
        val draft = persist(Schedule(workplace, LocalDate.of(2026, 11, 2)))
        request(shift(schedule = draft))
        val foreignWorkplace = persist(Workplace("외부 매장", "PREVIEW2"))
        val foreignSchedule = persist(Schedule(foreignWorkplace, published.weekStartDate).apply {
            publish(now.minusDays(1))
        })
        // 잘못된 교차 근무지 참조가 있어도 외부 근무의 요청을 노출하지 않는다.
        persist(SubstituteCandidate(request(shift(schedule = foreignSchedule)), target))
        flushAndClear()

        val requests = repository.findAffectedRequests(workplaceId(), targetId(), now)

        assertThat(requests.map { it.id }).containsExactly(own.id, candidateRequest.id, accepted.id)
        assertThat(requests.map { it.status }).containsExactly(
            RequestStatus.OPEN, RequestStatus.OPEN, RequestStatus.ACCEPTED
        )
        // 영속성 Context를 닫아도 응답 구성에 쓰는 to-one은 초기화되어 있다.
        entityManager.clear()
        assertThat(requests.map { it.shift.schedule.weekStartDate })
            .containsOnly(LocalDate.of(2026, 10, 26))
        assertThat(requests.map { it.shift.member.role }).containsOnly(WorkplaceRole.EMPLOYEE)
    }

    @Test
    fun fetchPendingAndAcceptedCandidatesWithoutRejectedOrUnrelatedRows() {
        val request = request(shift(assigned = other))
        val pending = persist(SubstituteCandidate(request, target))
        val accepted = persist(SubstituteCandidate(request, member())).apply { acceptRequest(now) }
        persist(SubstituteCandidate(request, member())).reject(now)
        persist(SubstituteCandidate(request(shift()), other))
        flushAndClear()

        val candidates = repository.findAffectedCandidates(listOf(requireNotNull(request.id)))
        entityManager.clear()

        assertThat(candidates.map { it.id }).containsExactly(pending.id, accepted.id)
        assertThat(candidates.map { it.member.workplace.name }).containsOnly("미리보기 매장")
        assertThat(candidates.map { it.request.shift.schedule.weekStartDate })
            .containsOnly(LocalDate.of(2026, 10, 26))
    }

    private fun member(): WorkplaceMember {
        val user = persist(User("preview-${++sequence}@test.com", "password", "직원$sequence"))
        return persist(WorkplaceMember(workplace, user, WorkplaceRole.EMPLOYEE, now.minusDays(10)))
    }

    private fun shift(
        schedule: Schedule = published,
        assigned: WorkplaceMember = target,
        start: LocalDateTime = now.plusDays(1),
        status: ShiftStatus = ShiftStatus.SCHEDULED
    ): Shift = persist(Shift(schedule, assigned, start, start.plusHours(4), status))

    private fun request(shift: Shift, status: RequestStatus = RequestStatus.OPEN): SubstituteRequest =
        persist(SubstituteRequest(shift, shift.member, status))

    private fun workplaceId(): Long = requireNotNull(workplace.id)
    private fun targetId(): Long = requireNotNull(target.id)
    private fun <T : Any> persist(entity: T): T = entityManager.persist(entity)
    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
