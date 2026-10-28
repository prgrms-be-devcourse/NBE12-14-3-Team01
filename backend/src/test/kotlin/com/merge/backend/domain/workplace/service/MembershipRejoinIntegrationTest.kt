package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.entity.RegularShiftPattern
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import jakarta.persistence.EntityManager
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
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
class MembershipRejoinIntegrationTest {
    @Autowired private lateinit var workplaceService: WorkplaceService
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var workplaceRepository: WorkplaceRepository
    @Autowired private lateinit var memberRepository: WorkplaceMemberRepository
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var clock: Clock

    private fun user(name: String): User =
        userRepository.save(User("$name@rejoin.test", "test-password", name))

    private fun endedMember(workplace: Workplace, user: User, daysAgo: Long): WorkplaceMember =
        memberRepository.saveAndFlush(
            WorkplaceMember(workplace, user, WorkplaceRole.EMPLOYEE,
                LocalDateTime.now(clock).minusDays(daysAgo + 10).withNano(0),
                LocalDateTime.now(clock).minusDays(daysAgo).withNano(0))
        )

    private fun reload() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun rejoinCreatesNewMembershipAndPreservesEndedRowAndPatternReference() {
        val employee = user("returning")
        val workplace = workplaceRepository.save(Workplace("rejoin", "AB820001"))
        val ended = endedMember(workplace, employee, 5)
        val oldId = requireNotNull(ended.id)
        val pattern = RegularShiftPattern(ended, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))
        entityManager.persist(pattern)
        reload()
        // Compare persisted timestamps on both sides of rejoin (MySQL DATETIME(6)).
        val baseline = memberRepository.findById(oldId).orElseThrow()
        val oldValues = listOf(baseline.joinedAt, baseline.leftAt, baseline.createDate, baseline.modifyDate)

        val newMember = workplaceService.joinWorkplace("AB820001", employee)
        val newId = requireNotNull(newMember.id)
        reload()

        val previous = memberRepository.findById(oldId).orElseThrow()
        val current = memberRepository.findById(newId).orElseThrow()
        assertThat(newId).isNotEqualTo(oldId)
        assertThat(current.role).isEqualTo(WorkplaceRole.EMPLOYEE)
        assertThat(current.leftAt).isNull()
        assertThat(current.activeMarker).isEqualTo(1)
        assertThat(current.joinedAt).isAfter(requireNotNull(previous.leftAt))
        assertThat(listOf(previous.joinedAt, previous.leftAt, previous.createDate, previous.modifyDate))
            .containsExactlyElementsOf(oldValues)
        assertThat(previous.role).isEqualTo(WorkplaceRole.EMPLOYEE)
        assertThat(previous.activeMarker).isNull()
        assertThat(entityManager.find(RegularShiftPattern::class.java, pattern.id).member.id).isEqualTo(oldId)
        assertThat(memberRepository.findAllByUser_IdAndLeftAtIsNull(employee.id).map { it.id })
            .containsExactly(newId)
        assertThat(memberRepository.findAllByUser_IdAndLeftAtIsNotNullOrderByLeftAtDescIdDesc(requireNotNull(employee.id))
            .map { it.id }).containsExactly(oldId)
    }

    @Test
    fun regeneratedInviteCodeRejectsOldCodeAndAcceptsNewCodeThroughHttp() {
        val manager = user("manager")
        val employee = user("invitee")
        val workplace = workplaceService.createWorkplace("invite regeneration", manager).workplace
        val workplaceId = requireNotNull(workplace.id)
        val previousCode = workplace.inviteCode
        val beforeCount = memberRepository.count()

        mvc.perform(post("/api/v1/workplaces/{id}/invite-code", workplaceId).contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + userService.genAccessToken(manager)))
            .andExpect(status().isOk).andExpect(jsonPath("$.success").value(true))
        reload()
        val newCode = workplaceRepository.findById(workplaceId).orElseThrow().inviteCode
        assertThat(newCode).isNotEqualTo(previousCode)
        val employeeToken = userService.genAccessToken(employee)

        mvc.perform(post("/api/v1/workplaces/join").contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
            .contentType(MediaType.APPLICATION_JSON).content("""{"inviteCode":"$previousCode"}"""))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("WPL-001"))
        assertThat(memberRepository.count()).isEqualTo(beforeCount)

        mvc.perform(post("/api/v1/workplaces/join").contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
            .contentType(MediaType.APPLICATION_JSON).content("""{"inviteCode":"$newCode"}"""))
            .andExpect(status().isCreated).andExpect(jsonPath("$.success").value(true))
        reload()
        assertThat(memberRepository.findByWorkplace_IdAndUser_IdAndLeftAtIsNull(workplaceId, employee.id))
            .isPresent()
        assertThat(memberRepository.count()).isEqualTo(beforeCount + 1)
    }

    @Test
    fun endedMembershipHttpHistoryContainsOnlyActorHistoryAndExcludesCurrentMembership() {
        val actor = user("history-owner")
        val other = user("history-other")
        val workplace = workplaceRepository.save(Workplace("history", "AB820002"))
        val oldest = endedMember(workplace, actor, 20)
        val latest = endedMember(workplace, actor, 5)
        val otherEnded = endedMember(workplace, other, 2)
        workplaceService.joinWorkplace("AB820002", actor)
        reload()

        mvc.perform(get("/api/v1/me/memberships").contextPath("/api/v1").param("status", "ENDED")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + userService.genAccessToken(actor)))
            .andExpect(status().isOk).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.memberships.length()").value(2))
            .andExpect(jsonPath("$.data.memberships[0].memberId").value(requireNotNull(latest.id)))
            .andExpect(jsonPath("$.data.memberships[1].memberId").value(requireNotNull(oldest.id)))
            .andExpect(jsonPath("$.data.memberships[0].workplaceName").value("history"))
            .andExpect(jsonPath("$.data.memberships[0].role").value("EMPLOYEE"))

        mvc.perform(get("/api/v1/me/memberships").contextPath("/api/v1").param("status", "ENDED")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + userService.genAccessToken(other)))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.memberships.length()").value(1))
            .andExpect(jsonPath("$.data.memberships[0].memberId").value(requireNotNull(otherEnded.id)))
    }

    @Test
    fun currentMembershipJoinReturnsConflictWithoutCreatingAnotherRow() {
        val employee = user("already-rejoined")
        val workplace = workplaceRepository.save(Workplace("duplicate join", "AB820004"))
        endedMember(workplace, employee, 5)
        val current = workplaceService.joinWorkplace("AB820004", employee)
        reload()
        val beforeCount = memberRepository.count()

        mvc.perform(post("/api/v1/workplaces/join").contextPath("/api/v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + userService.genAccessToken(employee))
            .contentType(MediaType.APPLICATION_JSON).content("""{"inviteCode":"AB820004"}"""))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("WPL-002"))
        reload()
        assertThat(memberRepository.count()).isEqualTo(beforeCount)
        assertThat(memberRepository.findAllByUser_IdAndLeftAtIsNull(employee.id).map { it.id })
            .containsExactly(current.id)
    }
    @Test
    fun schemaAllowsMultipleEndedMembershipsButRejectsAnotherCurrentMembership() {
        val employee = user("unique-guard")
        val workplace = workplaceRepository.save(Workplace("unique", "AB820003"))
        endedMember(workplace, employee, 20)
        endedMember(workplace, employee, 5)
        workplaceService.joinWorkplace("AB820003", employee)
        reload()

        val uniqueColumns = jdbc.queryForList(
            """SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
               FROM information_schema.statistics
               WHERE table_schema=DATABASE() AND table_name='workplace_member'
                 AND non_unique=0 AND index_name<>'PRIMARY'
               GROUP BY index_name""", String::class.java)
        assertThat(uniqueColumns).contains("workplace_id,user_id,active_marker")
            .doesNotContain("workplace_id,user_id", "user_id,workplace_id")
        assertThat(memberRepository.findAllByUser_IdAndLeftAtIsNotNullOrderByLeftAtDescIdDesc(requireNotNull(employee.id)))
            .hasSize(2)
        assertThatThrownBy {
            memberRepository.saveAndFlush(WorkplaceMember(workplace, employee, WorkplaceRole.EMPLOYEE,
                LocalDateTime.now(clock)))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
            .hasStackTraceContaining("uk_u2_active_membership")
    }
}
