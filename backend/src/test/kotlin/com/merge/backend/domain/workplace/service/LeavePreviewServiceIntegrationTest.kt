package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import com.merge.backend.global.exception.BusinessException
import jakarta.persistence.EntityManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/** V3·V4 Entity는 Fixture로만 사용한다. 상태 전이 Command나 미확정 HTTP 계약은 검증하지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
@Transactional(isolation = Isolation.REPEATABLE_READ)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LeavePreviewServiceIntegrationTest {
    @Autowired private lateinit var service: LeavePreviewService
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var testClock: TestClock

    private lateinit var workplace: Workplace
    private lateinit var manager: WorkplaceMember
    private lateinit var target: WorkplaceMember
    private lateinit var requester: WorkplaceMember
    private lateinit var published: Schedule
    private lateinit var draft: Schedule
    private lateinit var now: LocalDateTime
    private var userSequence = 0

    @BeforeEach
    fun setUp() {
        testClock.setInstant(Instant.parse("2026-09-15T01:00:00Z"))
        now = LocalDateTime.now(testClock)
        workplace = persist(Workplace("미리보기 매장", "PREV0001"))
        manager = member(WorkplaceRole.MANAGER)
        target = member()
        requester = member()
        published = persist(Schedule(workplace, LocalDate.of(2026, 9, 14)).apply {
            publish(now.minusDays(1))
        })
        draft = persist(Schedule(workplace, LocalDate.of(2026, 9, 21)))
    }

    @Test
    @DisplayName("영향이 없는 직원은 본인 소속 정보와 빈 영향 목록을 반환한다")
    fun emptyPreview() {
        val facts = preview()

        assertThat(facts.member.memberId).isEqualTo(target.id)
        assertThat(facts.member.name).isEqualTo(target.user.name)
        assertThat(facts.member.role).isEqualTo(WorkplaceRole.EMPLOYEE)
        assertThat(facts.member.joinedAt).isEqualTo(target.joinedAt)
        assertThat(facts.ongoingShifts).isEmpty()
        assertThat(facts.publishedShifts).isEmpty()
        assertThat(facts.draftShiftsToDelete).isEmpty()
        assertThat(facts.activeRequestsOnPublishedShifts).isEmpty()
        assertThat(facts.candidacies).isEmpty()
    }

    @Test
    @DisplayName("근무지 비소속자는 대상 존재 여부보다 먼저 WPL-003으로 거부한다")
    fun rejectOutsiderBeforeLookingUpTarget() {
        val outsider = persist(User("preview-outsider@test.com", "pw", "외부인"))
        assertError(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER) {
            service.getPreviewFacts(requireNotNull(outsider.id), requireNotNull(workplace.id), Long.MAX_VALUE)
        }
    }

    @Test
    @DisplayName("현재 직원은 관리자 권한이 없어 WPL-004로 거부한다")
    fun rejectEmployeeActor() {
        assertError(WorkplaceErrorCode.MANAGER_REQUIRED) {
            service.getPreviewFacts(requireNotNull(requester.user.id), requireNotNull(workplace.id),
                requireNotNull(target.id))
        }
    }

    @Test
    @DisplayName("종료된 관리자 소속은 현재 관리자 권한으로 사용할 수 없다")
    fun rejectEndedManagerActor() {
        val endedManager = member(WorkplaceRole.MANAGER, leftAt = now.minusDays(1))
        assertError(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER) {
            service.getPreviewFacts(requireNotNull(endedManager.user.id), requireNotNull(workplace.id),
                requireNotNull(target.id))
        }
    }

    @Test
    @DisplayName("없는 소속과 다른 근무지의 소속은 동일하게 WPL-006으로 거부한다")
    fun rejectMissingOrForeignTarget() {
        val otherWorkplace = persist(Workplace("다른 매장", "PREV0002"))
        val foreignMember = member(at = otherWorkplace)
        for (memberId in listOf(Long.MAX_VALUE, requireNotNull(foreignMember.id))) {
            assertError(WorkplaceErrorCode.WORKPLACE_MEMBER_NOT_FOUND) {
                service.getPreviewFacts(requireNotNull(manager.user.id), requireNotNull(workplace.id), memberId)
            }
        }
    }

    @Test
    @DisplayName("관리자를 퇴사 대상으로 조회하면 WPL-007로 거부한다")
    fun rejectManagerTarget() {
        assertError(WorkplaceErrorCode.MANAGER_CANNOT_LEAVE) {
            service.getPreviewFacts(requireNotNull(manager.user.id), requireNotNull(workplace.id),
                requireNotNull(manager.id))
        }
    }

    @Test
    @DisplayName("이미 종료된 소속을 퇴사 대상으로 조회하면 WPL-008로 거부한다")
    fun rejectEndedTarget() {
        val ended = member(leftAt = now.minusDays(1))
        assertError(WorkplaceErrorCode.MEMBERSHIP_ALREADY_ENDED) {
            service.getPreviewFacts(requireNotNull(manager.user.id), requireNotNull(workplace.id),
                requireNotNull(ended.id))
        }
    }

    @Test
    @DisplayName("공식 근무의 시작·종료 경계와 DRAFT 범위를 구분하고 식별·상태·시간을 보존한다")
    fun classifyShiftsAndPreserveComparisonFacts() {
        val ongoing = shift(start = now.minusHours(1), end = now.plusHours(1))
        val startingNow = shift(start = now, end = now.plusHours(2))
        val future = shift(start = now.plusDays(1), end = now.plusDays(1).plusHours(4))
        val draftShift = shift(schedule = draft, start = now.minusDays(1), end = now.minusDays(1).plusHours(4))
        shift(start = now.minusHours(2), end = now)
        shift(start = now.plusDays(2), end = now.plusDays(2).plusHours(4), status = ShiftStatus.CANCELLED)
        shift(member = requester)
        shift(schedule = draft, member = requester)

        val facts = preview()

        assertThat(facts.ongoingShifts.map { it.shiftId }).containsExactly(ongoing.id, startingNow.id)
        assertThat(facts.publishedShifts.map { it.shiftId }).containsExactly(future.id)
        assertThat(facts.draftShiftsToDelete.map { it.shiftId }).containsExactly(draftShift.id)
        val official = facts.publishedShifts.single()
        assertThat(official.memberId).isEqualTo(target.id)
        assertThat(official.scheduleId).isEqualTo(published.id)
        assertThat(official.scheduleStatus).isEqualTo(ScheduleStatus.PUBLISHED)
        assertThat(official.shiftStatus).isEqualTo(ShiftStatus.SCHEDULED)
        assertThat(official.startAt).isEqualTo(future.startAt)
        assertThat(official.endAt).isEqualTo(future.endAt)
        val deletion = facts.draftShiftsToDelete.single()
        assertThat(deletion.memberId).isEqualTo(target.id)
        assertThat(deletion.scheduleId).isEqualTo(draft.id)
        assertThat(deletion.scheduleStatus).isEqualTo(ScheduleStatus.DRAFT)
        assertThat(deletion.weekStartDate).isEqualTo(draft.weekStartDate)
        assertThat(deletion.startAt).isEqualTo(draftShift.startAt)
        assertThat(deletion.endAt).isEqualTo(draftShift.endAt)
    }

    @Test
    @DisplayName("퇴사자 공식 근무의 활성 요청만 종료 예상으로 조회하며 DB 상태를 바꾸지 않는다")
    fun previewOwnActiveRequestsWithoutChangingDatabase() {
        val ownShift = shift()
        val open = request(ownShift)
        val accepted = request(shift(start = now.plusDays(2), end = now.plusDays(2).plusHours(4)))
        val acceptedCandidate = candidate(accepted, requester).apply { acceptRequest(now) }
        request(ownShift, RequestStatus.CLOSED)
        request(shift(schedule = draft))
        request(shift(start = now.minusHours(1), end = now.plusHours(1)))
        val before = databaseState()

        val facts = preview()
        entityManager.flush()
        entityManager.clear()

        assertThat(facts.activeRequestsOnPublishedShifts.map { it.requestId })
            .containsExactly(open.id, accepted.id)
        assertThat(facts.activeRequestsOnPublishedShifts.map { it.status })
            .containsExactly(RequestStatus.OPEN, RequestStatus.ACCEPTED)
        assertThat(facts.activeRequestsOnPublishedShifts).allSatisfy {
            assertThat(it.requesterMemberId).isEqualTo(target.id)
            assertThat(it.wouldCloseRequest).isTrue()
        }
        assertThat(facts.candidacies).isEmpty()
        assertThat(entityManager.find(SubstituteCandidate::class.java, acceptedCandidate.id).status)
            .isEqualTo(CandidateStatus.ACCEPTED)
        assertThat(databaseState()).isEqualTo(before)
    }

    @Test
    @DisplayName("다른 PENDING 후보 중 현재 EMPLOYEE만 계산하고 중복 없이 요청 종료 영향을 예측한다")
    fun countOnlyCurrentEmployeePendingCandidates() {
        val request = request(shift(member = requester))
        val targetCandidate = candidate(request, target)
        candidate(request, manager)
        candidate(request, member(leftAt = now.minusDays(1)))
        candidate(request, member()).reject(now)
        val eligible = candidate(request, member())

        val initial = preview().candidacies.single()
        assertThat(initial.candidateId).isEqualTo(targetCandidate.id)
        assertThat(initial.memberId).isEqualTo(target.id)
        assertThat(initial.requestId).isEqualTo(request.id)
        assertThat(initial.requesterMemberId).isEqualTo(requester.id)
        assertThat(initial.shift.shiftId).isEqualTo(request.shift.id)
        assertThat(initial.otherCurrentPendingCandidates).isEqualTo(1)
        assertThat(initial.wouldBecomeIneligible).isTrue()
        assertThat(initial.wouldCloseRequest).isFalse()

        entityManager.find(SubstituteCandidate::class.java, eligible.id).reject(now)
        val before = databaseState()
        val lastPending = preview().candidacies.single()
        assertThat(lastPending.otherCurrentPendingCandidates).isZero()
        assertThat(lastPending.wouldCloseRequest).isTrue()
        assertThat(lastPending.candidateStatus).isEqualTo(CandidateStatus.PENDING)
        assertThat(databaseState()).isEqualTo(before)
    }

    @Test
    @DisplayName("수락 이력은 유지하고 ACCEPTED 요청의 PENDING과 ACCEPTED 후보 종료 효과를 구분한다")
    fun distinguishPendingAndAcceptedEffectsOnAcceptedRequests() {
        val acceptedRequest = request(shift(member = requester))
        val acceptedTarget = candidate(acceptedRequest, target).apply { acceptRequest(now) }
        val pendingRequest = request(shift(member = requester))
        val pendingTarget = candidate(pendingRequest, target)
        candidate(pendingRequest, member()).acceptRequest(now)
        val before = databaseState()

        val facts = preview()

        val accepted = facts.candidacies.single { it.candidateId == acceptedTarget.id }
        assertThat(accepted.candidateStatus).isEqualTo(CandidateStatus.ACCEPTED)
        assertThat(accepted.requestStatus).isEqualTo(RequestStatus.ACCEPTED)
        assertThat(accepted.wouldBecomeIneligible).isFalse()
        assertThat(accepted.wouldCloseRequest).isTrue()
        val pending = facts.candidacies.single { it.candidateId == pendingTarget.id }
        assertThat(pending.requestStatus).isEqualTo(RequestStatus.ACCEPTED)
        assertThat(pending.otherCurrentPendingCandidates).isZero()
        assertThat(pending.wouldBecomeIneligible).isTrue()
        assertThat(pending.wouldCloseRequest).isFalse()
        assertThat(databaseState()).isEqualTo(before)
    }

    @Test
    @DisplayName("종료·승인된 요청과 다른 근무지·지난 근무의 후보는 현재 영향에 포함하지 않는다")
    fun excludeInactiveAndForeignCandidacies() {
        candidate(request(shift(member = requester), RequestStatus.CLOSED), target)
        candidate(request(shift(member = requester), RequestStatus.APPROVED), target)
        candidate(request(shift(member = requester, start = now.minusHours(1), end = now.plusHours(1))), target)
        candidate(request(shift(schedule = draft, member = requester)), target)
        val otherWorkplace = persist(Workplace("외부 매장", "PREV0003"))
        val otherSchedule = persist(Schedule(otherWorkplace, published.weekStartDate).apply {
            publish(now.minusDays(1))
        })
        candidate(request(shift(schedule = otherSchedule, member = member(at = otherWorkplace))), target)

        assertThat(preview().candidacies).isEmpty()
    }

    private fun preview(): LeavePreviewFacts {
        entityManager.flush()
        entityManager.clear()
        return service.getPreviewFacts(requireNotNull(manager.user.id), requireNotNull(workplace.id),
            requireNotNull(target.id))
    }

    private fun member(
        role: WorkplaceRole = WorkplaceRole.EMPLOYEE,
        at: Workplace = workplace,
        leftAt: LocalDateTime? = null
    ): WorkplaceMember {
        val user = persist(User("preview-${++userSequence}@test.com", "pw", "직원$userSequence"))
        return persist(WorkplaceMember(at, user, role, now.minusDays(10), leftAt))
    }

    private fun shift(
        schedule: Schedule = published,
        member: WorkplaceMember = target,
        start: LocalDateTime = now.plusDays(1),
        end: LocalDateTime = start.plusHours(4),
        status: ShiftStatus = ShiftStatus.SCHEDULED
    ): Shift = persist(Shift(schedule, member, start, end, status))

    private fun request(shift: Shift, status: RequestStatus = RequestStatus.OPEN): SubstituteRequest =
        persist(SubstituteRequest(shift, shift.member, status))

    private fun candidate(request: SubstituteRequest, member: WorkplaceMember): SubstituteCandidate =
        persist(SubstituteCandidate(request, member))

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }

    private fun assertError(code: WorkplaceErrorCode, action: () -> Unit) {
        val exception = assertThrows<BusinessException> { action() }
        assertThat(exception.errorCode).isSameAs(code)
    }

    private fun databaseState(): Map<String, List<List<Any?>>> {
        entityManager.flush()
        return listOf(
            "SELECT id, left_at, active_marker, modify_date FROM workplace_member ORDER BY id",
            "SELECT id, status, published_at, modify_date FROM schedule ORDER BY id",
            "SELECT id, member_id, status, start_at, end_at, modify_date FROM shift ORDER BY id",
            "SELECT id, status, close_reason, approved_at, closed_at, modify_date FROM substitute_request ORDER BY id",
            "SELECT id, status, responded_at, modify_date FROM substitute_candidate ORDER BY id"
        ).associateWith { sql ->
            entityManager.createNativeQuery(sql).resultList.map { row -> (row as Array<*>).toList() }
        }
    }
}
