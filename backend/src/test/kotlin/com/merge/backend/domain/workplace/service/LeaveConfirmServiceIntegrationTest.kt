package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.entity.RegularShiftPattern
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftAssignmentHistory
import com.merge.backend.domain.shift.entity.ShiftAssignmentReason
import com.merge.backend.domain.shift.entity.ShiftCancellationReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.entity.UnavailableTime
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.controller.LeaveConfirmController
import com.merge.backend.domain.workplace.dto.request.LeaveConfirmRequest
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.dto.ValidationErrorResponse
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.GlobalExceptionHandler
import com.merge.backend.global.exception.GlobalErrorCode
import com.merge.backend.global.rq.Rq
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.util.AopTestUtils
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.module.kotlin.jacksonObjectMapper

@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(60)
class LeaveConfirmServiceIntegrationTest {
    @Autowired private lateinit var service: LeaveConfirmService
    @Autowired private lateinit var preview: LeavePreviewService
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var manager: PlatformTransactionManager
    @Autowired private lateinit var clock: TestClock
    @Autowired private lateinit var context: ApplicationContext
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var candidacyAdapter: LeaveCandidacyAdapter
    @MockitoSpyBean private lateinit var writer: LeaveMembershipWriter
    private lateinit var tx: TransactionTemplate
    private lateinit var now: LocalDateTime
    private var wp = 0L
    private var actorUser = 0L
    private var leaving = 0L
    private var target = 0L
    private var targetUser = 0L
    private var published = 0L
    private var draftSchedule = 0L
    private var first = 0L
    private var second = 0L
    private var draft = 0L
    private var requestId = 0L
    private var patternId = 0L
    private val users = mutableListOf<Long>()
    private val executor = Executors.newFixedThreadPool(2)

    @BeforeEach
    fun setUp() {
        tx = TransactionTemplate(manager)
        clock.setInstant(Instant.parse("2026-10-29T03:00:00Z"))
        now = LocalDateTime.now(clock)
        tx.executeWithoutResult {
            val workplace = persist(Workplace("퇴사 확정 테스트", UUID.randomUUID().toString().take(8)))
            wp = requireNotNull(workplace.id)
            fun member(role: WorkplaceRole = WorkplaceRole.EMPLOYEE): WorkplaceMember {
                val user = persist(User("${UUID.randomUUID()}@leave.test", "pw", "구성원"))
                users += requireNotNull(user.id)
                return persist(WorkplaceMember(workplace, user, role, now.minusMonths(1)))
            }
            val actor = member(WorkplaceRole.MANAGER)
            val employee = member()
            val handover = member()
            actorUser = requireNotNull(actor.user.id)
            leaving = requireNotNull(employee.id)
            target = requireNotNull(handover.id)
            targetUser = requireNotNull(handover.user.id)
            val schedule = persist(Schedule(workplace, LocalDate.of(2026, 10, 26)).apply { publish(now.minusDays(1)) })
            val draftSc = persist(Schedule(workplace, LocalDate.of(2026, 11, 2)))
            published = requireNotNull(schedule.id)
            draftSchedule = requireNotNull(draftSc.id)
            val shift = persist(Shift(schedule, employee, now.plusDays(1), now.plusDays(1).plusHours(4), ShiftStatus.SCHEDULED))
            first = requireNotNull(shift.id)
            second = requireNotNull(persist(Shift(schedule, employee, now.plusDays(2), now.plusDays(2).plusHours(4), ShiftStatus.SCHEDULED)).id)
            draft = requireNotNull(persist(Shift(draftSc, employee, now.plusDays(5), now.plusDays(5).plusHours(4), ShiftStatus.SCHEDULED)).id)
            val request = persist(SubstituteRequest(shift, employee, RequestStatus.OPEN))
            requestId = requireNotNull(request.id)
            persist(SubstituteCandidate(request, handover))
            patternId = requireNotNull(persist(RegularShiftPattern(employee, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0))).id)
        }
    }

    @AfterEach
    fun cleanUp() {
        executor.shutdownNow()
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
        tx.executeWithoutResult {
            for (query in listOf(
                // POST 활성화로 퇴사 확정이 알림(V5-N-20)을 만들므로 소속보다 먼저 지운다
                "DELETE FROM Notification n WHERE n.recipientMember.workplace.id = :wp",
                "DELETE FROM ShiftAssignmentHistory h WHERE h.shift.schedule.workplace.id = :wp",
                "DELETE FROM SubstituteCandidate c WHERE c.request.shift.schedule.workplace.id = :wp",
                "DELETE FROM SubstituteRequest r WHERE r.shift.schedule.workplace.id = :wp",
                "DELETE FROM Shift s WHERE s.schedule.workplace.id = :wp",
                "DELETE FROM RegularShiftPattern p WHERE p.member.workplace.id = :wp",
                "DELETE FROM Schedule s WHERE s.workplace.id = :wp",
                "DELETE FROM WorkplaceMember m WHERE m.workplace.id = :wp"
            )) entityManager.createQuery(query).setParameter("wp", wp).executeUpdate()
            entityManager.remove(entityManager.find(Workplace::class.java, wp))
            entityManager.createQuery("DELETE FROM UnavailableTime u WHERE u.user.id IN :ids").setParameter("ids", users).executeUpdate()
            users.forEach { entityManager.remove(entityManager.find(User::class.java, it)) }
        }
    }

    @Test
    fun commitsReassignmentCancellationDraftDeletionPatternPreservationAndInviteRotationTogether() {
        val response = service.confirm(actorUser, wp, leaving, command().copy(regenerateInviteCode = true))
        assertThat(response.reassignedShifts.single().memberId).isEqualTo(target)
        assertThat(response.closedRequests.total).isEqualTo(1)
        assertThat(response.closedRequests.byReason["SHIFT_CHANGED"]).isEqualTo(1)
        tx.executeWithoutResult {
            assertThat(entityManager.find(WorkplaceMember::class.java, leaving).leftAt).isEqualTo(now)
            assertThat(entityManager.find(Shift::class.java, first).member.id).isEqualTo(target)
            val cancelled = entityManager.find(Shift::class.java, second)
            assertThat(cancelled.status).isEqualTo(ShiftStatus.CANCELLED)
            assertThat(cancelled.cancellationReason).isEqualTo(ShiftCancellationReason.MEMBER_LEFT)
            assertThat(cancelled.cancelledAt).isEqualTo(now)
            assertThat(entityManager.find(Shift::class.java, draft)).isNull()
            assertThat(entityManager.find(RegularShiftPattern::class.java, patternId)).isNotNull()
            assertThat(entityManager.find(SubstituteRequest::class.java, requestId).closeReason).isEqualTo(RequestCloseReason.SHIFT_CHANGED)
            val history = entityManager.createQuery("SELECT h FROM ShiftAssignmentHistory h WHERE h.shift.id = :id",
                ShiftAssignmentHistory::class.java).setParameter("id", first).singleResult
            assertThat(history.reason).isEqualTo(ShiftAssignmentReason.MEMBER_LEFT)
            assertThat(history.previousMember.id).isEqualTo(leaving)
            assertThat(history.newMember.id).isEqualTo(target)
            assertThat(entityManager.find(Workplace::class.java, wp).inviteCode).isEqualTo(response.newInviteCode)
        }
    }

    @Test
    fun warningReportsOnlyClippedIntervalsAndConfirmationThenSucceeds() {
        tx.executeWithoutResult {
            persist(UnavailableTime(entityManager.find(User::class.java, targetUser), now.plusDays(1).minusHours(1), now.plusDays(1).plusHours(1)))
        }
        val command = command()
        val error = assertThrows<BusinessException> { service.confirm(actorUser, wp, leaving, command) }
        assertThat(error.errorCode).isEqualTo(WorkplaceErrorCode.LEAVE_UNAVAILABLE_CONFLICT_CONFIRMATION_REQUIRED)
        val warning = error.data as ConfirmationRequiredResponse
        val conflict = requireNotNull(warning.unavailableConflicts).single()
        assertThat(conflict.overlaps.single().startAt).isEqualTo(now.plusDays(1))
        assertUnchanged()
        service.confirm(actorUser, wp, leaving, command.copy(acknowledgedUnavailableConflicts = listOf(
            LeaveConfirmRequest.AcknowledgedUnavailableConflict(first, target, conflict.overlaps.map {
                LeaveConfirmRequest.Overlap(it.startAt, it.endAt)
            })
        )))
    }

    @Test
    fun publishedTimeChangesProduceStaleAndNoWrites() {
        val command = command()
        tx.executeWithoutResult {
            val shift = entityManager.find(Shift::class.java, first)
            shift.update(shift.member, shift.startAt, shift.endAt.plusMinutes(1))
        }
        assertThat(assertThrows<BusinessException> { service.confirm(actorUser, wp, leaving, command) }.errorCode)
            .isEqualTo(WorkplaceErrorCode.STALE_LEAVE_PLAN)
        assertUnchanged()
    }

    @Test
    fun permitsDraftReductionAndRejectsRepeatedCompletion() {
        val command = command()
        tx.executeWithoutResult { entityManager.remove(entityManager.find(Shift::class.java, draft)) }
        val response = service.confirm(actorUser, wp, leaving, command)
        assertThat(response.deletedDraftShifts).isEmpty()
        assertThat(assertThrows<BusinessException> { service.confirm(actorUser, wp, leaving, command) }.errorCode)
            .isEqualTo(WorkplaceErrorCode.MEMBERSHIP_ALREADY_ENDED)
    }

    @Test
    fun ongoingWorkAndExpiredContextRefuseWithoutWrites() {
        val command = command()
        clock.setInstant(clock.instant().plusSeconds(900))
        assertThat(assertThrows<BusinessException> { service.confirm(actorUser, wp, leaving, command) }.errorCode)
            .isEqualTo(WorkplaceErrorCode.STALE_LEAVE_PLAN)
        clock.setInstant(Instant.parse("2026-10-29T03:00:00Z"))
        tx.executeWithoutResult {
            val shift = entityManager.find(Shift::class.java, first)
            shift.update(shift.member, now, now.plusHours(2))
        }
        val fresh = command()
        val error = assertThrows<BusinessException> { service.confirm(actorUser, wp, leaving, fresh) }
        assertThat(error.errorCode).isEqualTo(WorkplaceErrorCode.ONGOING_SHIFT_PREVENTS_LEAVE)
        assertThat(error.data).isEqualTo(mapOf("endAt" to now.plusHours(2)))
        assertUnchanged()
    }

    @Test
    fun permissionPrecedesTargetLookupAndEndedHandoverTargetIsStale() {
        val command = command()
        assertThat(assertThrows<BusinessException> { service.confirm(targetUser, wp, Long.MAX_VALUE, command) }.errorCode)
            .isEqualTo(WorkplaceErrorCode.MANAGER_REQUIRED)
        assertThat(assertThrows<BusinessException> { service.confirm(actorUser, wp, Long.MAX_VALUE, command) }.errorCode)
            .isEqualTo(WorkplaceErrorCode.WORKPLACE_MEMBER_NOT_FOUND)
        tx.executeWithoutResult { entityManager.find(WorkplaceMember::class.java, target).endMembership(now) }
        assertThat(assertThrows<BusinessException> { service.confirm(actorUser, wp, leaving, command) }.errorCode)
            .isEqualTo(WorkplaceErrorCode.STALE_LEAVE_PLAN)
        assertUnchanged()
    }

    @Test
    fun duplicateAndIncompleteDecisionsAreStaleWithoutWrites() {
        val command = command()
        val decisions = requireNotNull(command.decisions)
        for (invalid in listOf(listOf(decisions.first()), listOf(decisions.first(), decisions.first()))) {
            assertThat(assertThrows<BusinessException> {
                service.confirm(actorUser, wp, leaving, command.copy(decisions = invalid))
            }.errorCode).isEqualTo(WorkplaceErrorCode.STALE_LEAVE_PLAN)
            assertUnchanged()
        }
    }

    @Test
    fun reusesExistingV3OfficialConflictErrorAndCannotBypassWithAcknowledgements() {
        val command = command()
        tx.executeWithoutResult {
            persist(Shift(entityManager.find(Schedule::class.java, published), entityManager.find(WorkplaceMember::class.java, target),
                now.plusDays(1), now.plusDays(1).plusHours(1), ShiftStatus.SCHEDULED))
        }
        val acknowledged = command.copy(acknowledgedUnavailableConflicts = listOf(
            LeaveConfirmRequest.AcknowledgedUnavailableConflict(first, target,
                listOf(LeaveConfirmRequest.Overlap(now.plusDays(1), now.plusDays(1).plusHours(4))))))
        assertThat(assertThrows<BusinessException> {
            service.confirm(actorUser, wp, leaving, acknowledged)
        }.errorCode).isEqualTo(ShiftErrorCode.DUPLICATE_LOCAL_SCHEDULE_TIME)
        assertUnchanged()
    }

    @Test
    fun concurrentConfirmationsProduceOneCompletionAndOneMembershipConflict() {
        val command = command()
        val start = CountDownLatch(1)
        val workers = (1..2).map {
            executor.submit<String> {
                check(start.await(10, TimeUnit.SECONDS))
                try { service.confirm(actorUser, wp, leaving, command); "SUCCESS" }
                catch (error: BusinessException) { error.errorCode.getCode() }
            }
        }
        start.countDown()
        assertThat(workers.map { it.get(20, TimeUnit.SECONDS) }).containsExactlyInAnyOrder("SUCCESS", "WPL-008")
        tx.executeWithoutResult {
            assertThat(entityManager.createQuery("SELECT COUNT(h) FROM ShiftAssignmentHistory h WHERE h.shift.id = :id",
                Long::class.javaObjectType).setParameter("id", first).singleResult).isEqualTo(1)
            assertThat(entityManager.find(WorkplaceMember::class.java, leaving).leftAt).isEqualTo(now)
        }
    }

    @Test
    fun lateFailureRollsBackClosedRequestsHistoryCancelledShiftAndDeletedDraft() {
        val targetWriter = AopTestUtils.getUltimateTargetObject<LeaveMembershipWriter>(writer)
        doThrow(IllegalStateException("injected membership failure")).whenever(targetWriter).complete(any(), any(), any())
        val error = assertThrows<IllegalStateException> { service.confirm(actorUser, wp, leaving, command().copy(regenerateInviteCode = true)) }
        assertThat(error).hasMessage("injected membership failure")
        assertUnchanged()
    }

    @Test
    fun missingV4BridgeBlocksCandidacyCleanupBeforeAllWrites() {
        tx.executeWithoutResult {
            val shift = persist(Shift(entityManager.find(Schedule::class.java, published), entityManager.find(WorkplaceMember::class.java, target),
                now.plusDays(3), now.plusDays(3).plusHours(4), ShiftStatus.SCHEDULED))
            val request = persist(SubstituteRequest(shift, shift.member, RequestStatus.OPEN))
            persist(SubstituteCandidate(request, entityManager.find(WorkplaceMember::class.java, leaving)))
        }
        // 2026-10-29 POST 활성화 뒤에도 기존 "연동 부재" 실패 시나리오(쓰기 전 중단)를 명시적으로 보존한다.
        val serviceTarget = AopTestUtils.getUltimateTargetObject<LeaveConfirmService>(service)
        val originalProvider = ReflectionTestUtils.getField(serviceTarget, "integration")
        ReflectionTestUtils.setField(serviceTarget, "integration",
            StaticListableBeanFactory().getBeanProvider(LeaveConfirmationIntegration::class.java))
        try {
            val error = assertThrows<IllegalStateException> { service.confirm(actorUser, wp, leaving, command()) }
            assertThat(error.message).contains("#92")
            assertUnchanged()
        } finally {
            ReflectionTestUtils.setField(serviceTarget, "integration", originalProvider)
        }
    }

    @Test
    fun postIsRegisteredWithV4IntegrationAndGuardReadiness() {
        // 2026-10-29 활성화: 소속 Guard(#105·#106) 뒤 Readiness Bean 제공. 두 Bean이 모두 있을 때만 Controller가 등록된다
        assertThat(context.getBeansOfType(LeaveConfirmationIntegration::class.java)).hasSize(1)
        assertThat(context.getBeansOfType(LeaveConfirmationReadiness::class.java)).hasSize(1)
        assertThat(context.getBeansOfType(LeaveConfirmController::class.java)).hasSize(1)
    }

    @Test
    fun standaloneHttpRequestCommitsRealV2V3V4Transaction() {
        val (requestId, candidateId) = addCandidacy()
        val rq = mock<Rq>()
        whenever(rq.actorId).thenReturn(actorUser)
        // Controller 객체를 직접 연결한 MockMvc (Filter 없이). Security Filter를 거치는 실제 경로는 LeaveConfirmHttpIntegrationTest
        val mvc = MockMvcBuilders.standaloneSetup(LeaveConfirmController(service, rq))
            .setControllerAdvice(GlobalExceptionHandler()).build()
        val body = jacksonObjectMapper().writeValueAsString(command())
        mvc.perform(post("/workplaces/{workplaceId}/members/{memberId}/leave", wp, leaving)
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.closedRequests.byReason.SHIFT_CHANGED").value(1))
            .andExpect(jsonPath("$.data.closedRequests.byReason.CANDIDATE_INELIGIBLE").value(1))
            .andExpect(jsonPath("$.data.ineligibleCandidacies").value(1))
        assertThat(context.getBeansOfType(LeaveConfirmController::class.java)).hasSize(1)
        tx.executeWithoutResult {
            assertThat(entityManager.find(WorkplaceMember::class.java, leaving).leftAt).isEqualTo(now)
            assertThat(entityManager.find(SubstituteCandidate::class.java, candidateId).status).isEqualTo(CandidateStatus.INELIGIBLE)
            assertThat(entityManager.find(SubstituteRequest::class.java, requestId).closeReason)
                .isEqualTo(RequestCloseReason.CANDIDATE_INELIGIBLE)
        }
    }

    @Test
    fun standaloneHttpConfirmationWarningLeavesDatabaseUnchanged() {
        tx.executeWithoutResult {
            persist(UnavailableTime(entityManager.find(User::class.java, targetUser),
                now.plusDays(1).minusHours(1), now.plusDays(1).plusHours(1)))
        }
        val rq = mock<Rq>()
        whenever(rq.actorId).thenReturn(actorUser)
        val mvc = MockMvcBuilders.standaloneSetup(LeaveConfirmController(service, rq))
            .setControllerAdvice(GlobalExceptionHandler()).build()
        mvc.perform(post("/workplaces/{workplaceId}/members/{memberId}/leave", wp, leaving)
            .contentType(MediaType.APPLICATION_JSON).content(jacksonObjectMapper().writeValueAsString(command())))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("WPL-011"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(true))
            .andExpect(jsonPath("$.data.unavailableConflicts[0].targetMemberId").value(target))
            .andExpect(jsonPath("$.data.unavailableConflicts[0].overlaps[0].startAt").value("2026-10-30T12:00:00"))
            .andExpect(jsonPath("$.data.unavailableConflicts[0].unavailableTimeId").doesNotExist())
        assertUnchanged()
        assertThat(context.getBeansOfType(LeaveConfirmController::class.java)).hasSize(1)
    }

    @Test
    fun invalidHandoverIsRejectedBeforeWaitingForAnExistingScheduleLock() {
        val original = command()
        val decisions = requireNotNull(original.decisions)
        val invalid = original.copy(decisions = decisions.map {
            if (it?.action == "REASSIGN") it.copy(targetMemberId = null) else it
        })
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = executor.submit { tx.executeWithoutResult {
            entityManager.find(Schedule::class.java, published, LockModeType.PESSIMISTIC_WRITE)
            held.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        } }
        try {
            check(held.await(10, TimeUnit.SECONDS))
            val attempted = executor.submit<BusinessException> {
                assertThrows<BusinessException> { service.confirm(actorUser, wp, leaving, invalid) }
            }
            val failure = attempted.get(3, TimeUnit.SECONDS)
            assertThat(failure.errorCode).isEqualTo(GlobalErrorCode.VALIDATION_ERROR)
            val details = failure.data as ValidationErrorResponse
            assertThat(details.fieldErrors.map { it.field }).contains("decisions[0].targetMemberId")
        } finally { release.countDown() }
        holder.get(10, TimeUnit.SECONDS)
        assertUnchanged()
    }

    @Test
    fun actualV4ClosesSolePendingCandidateAndCountsBothReasons() {
        val (requestId, candidateId) = addCandidacy()
        val result = service.confirm(actorUser, wp, leaving, command())
        assertThat(result.closedRequests.total).isEqualTo(2)
        assertThat(result.closedRequests.byReason).containsEntry("SHIFT_CHANGED", 1).containsEntry("CANDIDATE_INELIGIBLE", 1)
        assertThat(result.ineligibleCandidacies).isEqualTo(1)
        tx.executeWithoutResult {
            val candidate = entityManager.find(SubstituteCandidate::class.java, candidateId)
            assertThat(candidate.status).isEqualTo(CandidateStatus.INELIGIBLE)
            assertThat(candidate.respondedAt).isNull()
            val request = entityManager.find(SubstituteRequest::class.java, requestId)
            assertThat(request.status).isEqualTo(RequestStatus.CLOSED)
            assertThat(request.closeReason).isEqualTo(RequestCloseReason.CANDIDATE_INELIGIBLE)
            assertThat(entityManager.find(WorkplaceMember::class.java, leaving).leftAt).isEqualTo(now)
        }
    }

    @Test
    fun actualV4PreservesAcceptedCandidateHistoryWhileClosingRequest() {
        val (requestId, candidateId) = addCandidacy(accepted = true)
        val result = service.confirm(actorUser, wp, leaving, command())
        assertThat(result.ineligibleCandidacies).isZero()
        assertThat(result.closedRequests.byReason["CANDIDATE_INELIGIBLE"]).isEqualTo(1)
        tx.executeWithoutResult {
            val candidate = entityManager.find(SubstituteCandidate::class.java, candidateId)
            assertThat(candidate.status).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(candidate.respondedAt).isEqualTo(now.minusMinutes(1))
            assertThat(entityManager.find(SubstituteRequest::class.java, requestId).closeReason)
                .isEqualTo(RequestCloseReason.CANDIDATE_INELIGIBLE)
        }
    }

    @Test
    fun actualV4KeepsRequestOpenWhenAnotherRespondableCandidateRemains() {
        val (requestId, _) = addCandidacy()
        tx.executeWithoutResult {
            val user = persist(User("${UUID.randomUUID()}@leave.test", "pw", "다른 후보"))
            users += requireNotNull(user.id)
            val member = persist(WorkplaceMember(entityManager.find(Workplace::class.java, wp), user,
                WorkplaceRole.EMPLOYEE, now.minusMonths(1)))
            persist(SubstituteCandidate(entityManager.find(SubstituteRequest::class.java, requestId), member))
        }
        val result = service.confirm(actorUser, wp, leaving, command())
        assertThat(result.ineligibleCandidacies).isEqualTo(1)
        assertThat(result.closedRequests.byReason["CANDIDATE_INELIGIBLE"]).isZero()
        tx.executeWithoutResult {
            assertThat(entityManager.find(SubstituteRequest::class.java, requestId).status).isEqualTo(RequestStatus.OPEN)
        }
    }

    @Test
    fun failureAfterRealV4CleanupRollsBackCandidateAndAllV2V3Changes() {
        val (requestId, candidateId) = addCandidacy()
        val targetWriter = AopTestUtils.getUltimateTargetObject<LeaveMembershipWriter>(writer)
        doThrow(IllegalStateException("after real V4 cleanup")).whenever(targetWriter).complete(any(), any(), any())
        val error = assertThrows<IllegalStateException> {
            service.confirm(actorUser, wp, leaving, command().copy(regenerateInviteCode = true))
        }
        assertThat(error).hasMessage("after real V4 cleanup")
        assertUnchanged()
        tx.executeWithoutResult {
            assertThat(entityManager.find(SubstituteCandidate::class.java, candidateId).status).isEqualTo(CandidateStatus.PENDING)
            assertThat(entityManager.find(SubstituteRequest::class.java, requestId).status).isEqualTo(RequestStatus.OPEN)
        }
    }

    @Test
    fun v4RejectsAnUncoveredRequestWithoutWaitingForItsRowLock() {
        val (requestId, candidateId) = addCandidacy()
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = executor.submit { tx.executeWithoutResult {
            entityManager.find(SubstituteRequest::class.java, requestId, LockModeType.PESSIMISTIC_WRITE)
            held.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        } }
        try {
            check(held.await(10, TimeUnit.SECONDS))
            val attempted = executor.submit<BusinessException> {
                assertThrows<BusinessException> {
                    tx.executeWithoutResult { candidacyAdapter.cleanupCandidacies(leaving, now, emptySet()) }
                }
            }
            assertThat(attempted.get(3, TimeUnit.SECONDS).errorCode).isEqualTo(WorkplaceErrorCode.STALE_LEAVE_PLAN)
        } finally { release.countDown() }
        holder.get(10, TimeUnit.SECONDS)
        tx.executeWithoutResult {
            assertThat(entityManager.find(SubstituteCandidate::class.java, candidateId).status).isEqualTo(CandidateStatus.PENDING)
        }
        assertUnchanged()
    }

    @Test
    fun v4RefusesUnownedRequestLocksBeforeChangingCandidate() {
        val (requestId, candidateId) = addCandidacy()
        val error = assertThrows<IllegalStateException> {
            tx.executeWithoutResult { candidacyAdapter.cleanupCandidacies(leaving, now, setOf(requestId)) }
        }
        assertThat(error.message).contains("Request 쓰기 Lock")
        tx.executeWithoutResult {
            assertThat(entityManager.find(SubstituteCandidate::class.java, candidateId).status).isEqualTo(CandidateStatus.PENDING)
        }
    }

    @Test
    fun candidateAcceptanceDuringRequestLockWaitMakesOriginalImpactStale() {
        val (requestId, candidateId) = addCandidacy()
        val command = command()
        val error = raceWithLock("substitute_request", {
            entityManager.find(SubstituteRequest::class.java, requestId, LockModeType.PESSIMISTIC_WRITE)
            entityManager.find(SubstituteCandidate::class.java, candidateId).acceptRequest(now)
        }) { service.confirm(actorUser, wp, leaving, command) }
        assertThat((error as BusinessException).errorCode).isEqualTo(WorkplaceErrorCode.STALE_LEAVE_PLAN)
        assertUnchanged()
        tx.executeWithoutResult {
            assertThat(entityManager.find(SubstituteCandidate::class.java, candidateId).status).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(entityManager.find(SubstituteRequest::class.java, requestId).status).isEqualTo(RequestStatus.ACCEPTED)
        }
    }

    private fun addCandidacy(accepted: Boolean = false): Pair<Long, Long> = requireNotNull(tx.execute {
        val shift = persist(Shift(entityManager.find(Schedule::class.java, published), entityManager.find(WorkplaceMember::class.java, target),
            now.plusDays(3), now.plusDays(3).plusHours(4), ShiftStatus.SCHEDULED))
        val request = persist(SubstituteRequest(shift, shift.member, RequestStatus.OPEN))
        val candidate = persist(SubstituteCandidate(request, entityManager.find(WorkplaceMember::class.java, leaving)))
        if (accepted) candidate.acceptRequest(now.minusMinutes(1))
        requireNotNull(request.id) to requireNotNull(candidate.id)
    })

    @Test
    fun detectsDraftInsertedWhileWaitingForScheduleLock() {
        val command = command()
        raceWithLock("schedule", {
            entityManager.find(Schedule::class.java, draftSchedule, LockModeType.PESSIMISTIC_WRITE)
            persist(Shift(entityManager.find(Schedule::class.java, draftSchedule), entityManager.find(WorkplaceMember::class.java, leaving),
                now.plusDays(6), now.plusDays(6).plusHours(4), ShiftStatus.SCHEDULED))
        }) { service.confirm(actorUser, wp, leaving, command) }.also { error ->
            assertThat((error as BusinessException).errorCode).isEqualTo(WorkplaceErrorCode.STALE_LEAVE_PLAN)
        }
        assertUnchanged()
    }

    @Test
    fun recalculatesUnavailableConflictAfterWaitingForTargetUserLock() {
        val command = command()
        val error = raceWithLock("users", {
            val user = entityManager.find(User::class.java, targetUser, LockModeType.PESSIMISTIC_WRITE)
            persist(UnavailableTime(user, now.plusDays(1), now.plusDays(1).plusHours(1)))
        }) { service.confirm(actorUser, wp, leaving, command) }
        assertThat((error as BusinessException).errorCode)
            .isEqualTo(WorkplaceErrorCode.LEAVE_UNAVAILABLE_CONFLICT_CONFIRMATION_REQUIRED)
        assertUnchanged()
    }

    private fun raceWithLock(expectedTable: String, holder: () -> Unit, confirm: () -> Unit): Throwable {
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = executor.submit { tx.executeWithoutResult {
            holder()
            entityManager.flush()
            held.countDown()
            check(release.await(15, TimeUnit.SECONDS))
        } }
        check(held.await(15, TimeUnit.SECONDS))
        val second = executor.submit<Throwable> { try { confirm(); error("expected rejection") } catch (error: Throwable) { error } }
        try {
            assertThrows<TimeoutException> { second.get(250, TimeUnit.MILLISECONDS) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var waiting = false
            while (System.nanoTime() < deadline) {
                // test 계정의 자기 Session만 조회한다. 전역 PROCESS/performance_schema 권한은 필요 없다.
                val statements = jdbc.query("SHOW FULL PROCESSLIST") { row, _ -> row.getString("Info")?.lowercase().orEmpty() }
                if (statements.any { it.contains("from $expectedTable ") && it.contains("for update") }) {
                    waiting = true
                    break
                }
                Thread.sleep(25)
            }
            assertThat(waiting).describedAs("실제 MySQL Row Lock 대기").isTrue()
        } finally { release.countDown() }
        first.get(15, TimeUnit.SECONDS)
        return second.get(15, TimeUnit.SECONDS)
    }

    private fun command(): LeaveConfirmRequest {
        val original = preview.getPreview(actorUser, wp, leaving)
        return LeaveConfirmRequest(original.previewContext, original.publishedShifts.map {
            LeaveConfirmRequest.Decision(it.shiftId, it.startAt, it.endAt,
                if (it.shiftId == first) "REASSIGN" else "CANCEL", if (it.shiftId == first) target else null)
        })
    }

    private fun assertUnchanged() = tx.executeWithoutResult {
        assertThat(entityManager.find(WorkplaceMember::class.java, leaving).leftAt).isNull()
        assertThat(entityManager.find(Shift::class.java, first).member.id).isEqualTo(leaving)
        assertThat(entityManager.find(Shift::class.java, second).status).isEqualTo(ShiftStatus.SCHEDULED)
        assertThat(entityManager.find(Shift::class.java, draft)).isNotNull()
        assertThat(entityManager.find(SubstituteRequest::class.java, requestId).status).isEqualTo(RequestStatus.OPEN)
        assertThat(entityManager.createQuery("SELECT COUNT(h) FROM ShiftAssignmentHistory h WHERE h.shift.schedule.workplace.id = :wp",
            Long::class.javaObjectType).setParameter("wp", wp).singleResult).isZero()
    }

    private fun <T : Any> persist(value: T): T { entityManager.persist(value); return value }
}
