package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.dto.ShiftRequest
import com.merge.backend.domain.shift.entity.RegularShiftPattern
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.service.ScheduleService
import com.merge.backend.domain.shift.service.ShiftService
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.substitute.service.SubstituteCandidateService
import com.merge.backend.domain.substitute.service.SubstituteRequestService
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.global.exception.BusinessException
import jakarta.persistence.EntityManager
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class MembershipRejoinCompatibilityIntegrationTest {
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var workplaceService: WorkplaceService
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var scheduleService: ScheduleService
    @Autowired private lateinit var shiftService: ShiftService
    @Autowired private lateinit var shiftRepository: ShiftRepository
    @Autowired private lateinit var candidateService: SubstituteCandidateService
    @Autowired private lateinit var requestService: SubstituteRequestService
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var clock: Clock

    private lateinit var workplace: Workplace
    private lateinit var manager: WorkplaceMember
    private lateinit var employee: User
    private lateinit var ended: WorkplaceMember
    private lateinit var current: WorkplaceMember
    private lateinit var week: LocalDate

    private fun user(name: String): User = User("$name@compatibility.test", "test-password", name)
        .also { entityManager.persist(it) }

    private fun member(user: User, role: WorkplaceRole): WorkplaceMember =
        WorkplaceMember(workplace, user, role, LocalDateTime.now(clock).minusDays(20))
            .also { entityManager.persist(it) }

    @BeforeEach
    fun setUp() {
        workplace = Workplace("membership compatibility", "AB82CC01").also { entityManager.persist(it) }
        manager = member(user("manager"), WorkplaceRole.MANAGER)
        employee = user("returning")
        ended = WorkplaceMember(workplace, employee, WorkplaceRole.EMPLOYEE,
            LocalDateTime.now(clock).minusDays(20), LocalDateTime.now(clock).minusDays(5))
            .also { entityManager.persist(it) }
        current = workplaceService.joinWorkplace("AB82CC01", employee)
        week = LocalDate.now(clock).plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        reload()
    }

    private fun reload() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun publishedSchedule(): Schedule = Schedule(workplace, week).also {
        it.publish(LocalDateTime.now(clock).minusDays(1))
        entityManager.persist(it)
    }

    private fun shift(schedule: Schedule, member: WorkplaceMember, hour: Int = 9): Shift =
        Shift(schedule, member, week.atTime(hour, 0), week.atTime(hour + 2, 0), ShiftStatus.SCHEDULED)
            .also { entityManager.persist(it) }

    @Test
    fun regularPatternsAndDraftGenerationUseOnlyRejoinedMembership() {
        val oldPattern = RegularShiftPattern(ended, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))
        entityManager.persist(oldPattern)
        val managerToken = userService.genAccessToken(manager.user)
        mvc.perform(post("/api/v1/workplaces/{id}/regular-shift-patterns", workplace.id).contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $managerToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"memberId":${current.id},"dayOfWeek":"MONDAY","startTime":"13:00","endTime":"15:00"}"""))
            .andExpect(status().isCreated)
        mvc.perform(get("/api/v1/workplaces/{id}/regular-shift-patterns", workplace.id).contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $managerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].memberId").value(requireNotNull(current.id)))

        val draft = scheduleService.createDraftSchedule(manager.user.id, workplace.id, week)
        reload()
        assertThat(draft.shiftCount).isEqualTo(1)
        val shifts = shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(draft.scheduleId)
        assertThat(shifts.map { it.member.id }).containsExactly(current.id)
        assertThat(entityManager.find(RegularShiftPattern::class.java, oldPattern.id).member.id).isEqualTo(ended.id)
    }

    @Test
    fun shiftCreationRejectsEndedMembershipAndAcceptsNewMembership() {
        val schedule = Schedule(workplace, week).also { entityManager.persist(it) }
        assertThatThrownBy {
            shiftService.create(ShiftRequest(ended.id, week.atTime(9, 0), week.atTime(11, 0), false),
                workplace.id, schedule.id, manager.user.id)
        }.isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.errorCode).isEqualTo(ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE)
        }
        val created = shiftService.create(
            ShiftRequest(current.id, week.atTime(9, 0), week.atTime(11, 0), false),
            workplace.id, schedule.id, manager.user.id)
        reload()
        assertThat(shiftRepository.findById(requireNotNull(created.id)).orElseThrow().member.id).isEqualTo(current.id)
        assertThat(entityManager.find(WorkplaceMember::class.java, ended.id).leftAt).isNotNull()
    }

    @Test
    fun substituteCandidatesUseCurrentMembershipAndRejectOldCandidateResponse() {
        val requester = member(user("requester"), WorkplaceRole.EMPLOYEE)
        val target = shift(publishedSchedule(), requester)
        val request = SubstituteRequest(target, requester, RequestStatus.OPEN).also { entityManager.persist(it) }
        val oldCandidate = SubstituteCandidate(request, ended).also { entityManager.persist(it) }
        val newCandidate = SubstituteCandidate(request, current).also { entityManager.persist(it) }
        reload()

        assertThat(candidateService.findCandidates(requireNotNull(workplace.id), requireNotNull(requester.id),
            entityManager.find(Shift::class.java, target.id)).map { it.id }).containsExactly(current.id)
        assertThat(candidateService.findReceivedRequests(requireNotNull(employee.id)).map { it.id })
            .containsExactly(newCandidate.id)
        assertThatThrownBy {
            candidateService.acceptCandidate(requireNotNull(oldCandidate.id), requireNotNull(employee.id))
        }.isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.errorCode).isEqualTo(SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER)
        }
        candidateService.acceptCandidate(requireNotNull(newCandidate.id), requireNotNull(employee.id))
        reload()
        assertThat(candidateService.findAcceptedPendingRequests(requireNotNull(employee.id)).map { it.id })
            .containsExactly(newCandidate.id)
        assertThat(entityManager.find(SubstituteCandidate::class.java, oldCandidate.id).member.id).isEqualTo(ended.id)
    }

    @Test
    fun acceptedSubstituteFromEndedMembershipIsExcludedAfterRejoin() {
        val requester = member(user("accepted-requester"), WorkplaceRole.EMPLOYEE)
        val target = shift(publishedSchedule(), requester)
        val request = SubstituteRequest(target, requester, RequestStatus.OPEN).also { entityManager.persist(it) }
        val oldCandidate = SubstituteCandidate(request, ended).also {
            it.acceptRequest(LocalDateTime.now(clock).minusDays(6))
            entityManager.persist(it)
        }
        reload()

        assertThat(candidateService.findAcceptedPendingRequests(requireNotNull(employee.id)).map { it.id })
            .doesNotContain(oldCandidate.id)
        assertThat(entityManager.find(SubstituteCandidate::class.java, oldCandidate.id).member.id).isEqualTo(ended.id)
    }

    @Test
    fun rejoinedUserCannotRequestSubstituteForEndedMembershipShift() {
        member(user("available-candidate"), WorkplaceRole.EMPLOYEE)
        val oldShift = shift(publishedSchedule(), ended)
        reload()

        assertThatThrownBy {
            requestService.create(requireNotNull(oldShift.id), requireNotNull(employee.id))
        }.isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.errorCode).isEqualTo(SubstituteRequestErrorCode.NOT_OWN_SHIFT)
        }
    }

    @Test
    fun endedMembershipShiftIsExcludedFromBothListAndDetailAfterRejoin() {
        val oldShift = shift(publishedSchedule(), ended)
        reload()
        assertThat(shiftService.list(week, employee.id)).isEmpty()
        assertThatThrownBy {
            shiftService.detail(oldShift.id, employee.id)
        }.isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.errorCode).isEqualTo(ShiftErrorCode.FORBIDDEN_ACCESS)
        }
    }
}
