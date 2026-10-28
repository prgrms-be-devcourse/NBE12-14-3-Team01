package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.entity.Notification
import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.notification.repository.NotificationRepository
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.substitute.service.SubstituteCandidateService
import com.merge.backend.domain.substitute.service.SubstituteRequestService
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.tuple
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 대타 Event의 Commit 후 알림 생성을 실제 MySQL Row로 확인한다.
 * 업무 Transaction의 실제 Commit이 필요하므로 클래스에 @Transactional을 두지 않는다.
 * Fixture는 고유 접미사를 사용하며 테스트마다 별도로 Commit한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class SubstituteNotificationAfterCommitIntegrationTest {
    @MockitoSpyBean private lateinit var notificationRepository: NotificationRepository
    @Autowired private lateinit var substituteRequestService: SubstituteRequestService
    @Autowired private lateinit var substituteCandidateService: SubstituteCandidateService
    @Autowired private lateinit var substituteRequestRepository: SubstituteRequestRepository
    @Autowired private lateinit var substituteCandidateRepository: SubstituteCandidateRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var workplaceRepository: WorkplaceRepository
    @Autowired private lateinit var workplaceMemberRepository: WorkplaceMemberRepository
    @Autowired private lateinit var scheduleRepository: ScheduleRepository
    @Autowired private lateinit var shiftRepository: ShiftRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var testClock: TestClock
    @PersistenceContext private lateinit var entityManager: EntityManager

    @BeforeEach
    fun fixClock() {
        testClock.setInstant(FIXED_NOW)
    }

    @Test
    @DisplayName("대타 요청이 Commit되면 후보에게만 요청 도착 알림이 1건씩 저장된다")
    fun createRequest_savesReceivedNotificationOnlyForCandidatesAfterCommit() {
        val fixture = createRequestFixture()
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        val response = substituteRequestService.create(fixture.shiftId, fixture.requesterUserId)
        assertThat(response.requestCreated).isTrue()
        assertThat(response.candidateCount).isEqualTo(2)
        val requests = requestsOf(fixture.shiftId)
        assertThat(requests).hasSize(1)
        assertThat(requests[0].status).isEqualTo(RequestStatus.OPEN)
        assertThat(candidateMemberIdsOf(checkNotNull(requests[0].id)))
            .containsExactlyInAnyOrder(fixture.firstCandidateMemberId, fixture.secondCandidateMemberId)
        val notifications = notificationsOf(fixture.allMemberIds())
        assertThat(notifications.map { it.recipientMember.id })
            .containsExactlyInAnyOrder(fixture.firstCandidateMemberId, fixture.secondCandidateMemberId)
        notifications.forEach {
            assertThat(it.type).isEqualTo(NotificationType.SUBSTITUTE_REQUEST_RECEIVED)
            assertThat(it.message).isEqualTo(REQUEST_RECEIVED_MESSAGE)
            assertThat(it.readAt).isNull()
        }
    }

    @Test
    @DisplayName("후보가 없으면 Request 없이 Commit되고 현재 MANAGER에게만 후보 없음 알림이 1건 저장된다")
    fun createRequestWithoutCandidate_savesNoCandidateNotificationOnlyForActiveManagers() {
        val fixture = createNoCandidateFixture()
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        val response = substituteRequestService.create(fixture.shiftId, fixture.requesterUserId)
        assertThat(response.requestCreated).isFalse()
        assertThat(response.requestId).isNull()
        assertThat(response.candidateCount).isZero()
        assertThat(requestsOf(fixture.shiftId)).isEmpty()
        assertNotifiedOnly(
            notificationsOf(fixture.allMemberIds()),
            listOf(fixture.activeManagerMemberId),
            NotificationType.SUBSTITUTE_NO_CANDIDATE,
            NO_CANDIDATE_MESSAGE,
        )
    }

    @Test
    @DisplayName("후보가 수락하면 Commit 후 현재 MANAGER 전원에게만 수락 알림이 1건씩 저장된다")
    fun acceptCandidate_savesAcceptedNotificationOnlyForActiveManagersAfterCommit() {
        val fixture = createOpenRequestFixture()
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        substituteCandidateService.acceptCandidate(fixture.firstCandidateId, fixture.firstCandidateUserId)
        assertThat(requestStatusOf(fixture.requestId)).isEqualTo(RequestStatus.ACCEPTED)
        assertThat(candidateStatusOf(fixture.firstCandidateId)).isEqualTo(CandidateStatus.ACCEPTED)
        assertThat(candidateStatusOf(fixture.secondCandidateId)).isEqualTo(CandidateStatus.PENDING)
        assertNotifiedOnly(
            notificationsOf(fixture.allMemberIds()),
            listOf(fixture.firstManagerMemberId, fixture.secondManagerMemberId),
            NotificationType.SUBSTITUTE_ACCEPTED,
            ACCEPTED_MESSAGE,
        )
    }

    @Test
    @DisplayName("마지막 후보가 거절하면 Request가 종료되고 요청자와 현재 MANAGER에게만 전원 거절 알림이 저장된다")
    fun rejectLastCandidate_savesAllRejectedNotificationForRequesterAndActiveManagers() {
        val fixture = createSingleCandidateRequestFixture()
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        substituteCandidateService.rejectCandidate(fixture.candidateId, fixture.candidateUserId)
        val request = requestOf(fixture.requestId)
        assertThat(request.status).isEqualTo(RequestStatus.CLOSED)
        assertThat(request.closeReason).isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED)
        assertThat(candidateStatusOf(fixture.candidateId)).isEqualTo(CandidateStatus.REJECTED)
        assertNotifiedOnly(
            notificationsOf(fixture.allMemberIds()),
            listOf(fixture.requesterMemberId, fixture.activeManagerMemberId),
            NotificationType.SUBSTITUTE_ALL_REJECTED,
            ALL_REJECTED_MESSAGE,
        )
    }

    @Test
    @DisplayName("다른 후보가 아직 PENDING이면 거절해도 Request는 OPEN이고 관련 구성원에게 알림이 저장되지 않는다")
    fun rejectNotLastCandidate_keepsRequestOpenAndSavesNoNotification() {
        val fixture = createOpenRequestFixture()
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        substituteCandidateService.rejectCandidate(fixture.firstCandidateId, fixture.firstCandidateUserId)
        val request = requestOf(fixture.requestId)
        assertThat(request.status).isEqualTo(RequestStatus.OPEN)
        assertThat(request.closeReason).isNull()
        assertThat(candidateStatusOf(fixture.firstCandidateId)).isEqualTo(CandidateStatus.REJECTED)
        assertThat(candidateStatusOf(fixture.secondCandidateId)).isEqualTo(CandidateStatus.PENDING)
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
    }

    @Test
    @DisplayName("MANAGER가 승인하면 Shift 담당자가 수락자로 바뀌고 요청자와 수락자에게 각자의 승인 알림이 저장된다")
    fun approveRequest_movesShiftAndSavesApprovedNotificationsForRequesterAndAccepted() {
        val fixture = createManagerActionFixture(true, false)
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        substituteRequestService.approve(fixture.requestId, fixture.managerUserId)
        assertThat(requestOf(fixture.requestId).status).isEqualTo(RequestStatus.APPROVED)
        assertThat(shiftMemberIdOf(fixture.shiftId)).isEqualTo(fixture.firstCandidateMemberId)
        val notifications = notificationsOf(fixture.allMemberIds())
        assertThat(notifications.map {
            tuple(it.recipientMember.id, it.type, it.message, it.readAt)
        }).containsExactlyInAnyOrder(
            tuple(fixture.requesterMemberId, NotificationType.SUBSTITUTE_APPROVED,
                APPROVED_REQUESTER_MESSAGE, null),
            tuple(fixture.firstCandidateMemberId, NotificationType.SUBSTITUTE_APPROVED,
                APPROVED_ACCEPTED_MESSAGE, null),
        )
    }

    @Test
    @DisplayName("OPEN Request를 MANAGER가 종료하면 요청자와 PENDING 후보에게만 종료 알림이 저장되고 담당자는 그대로다")
    fun closeOpenRequest_savesClosedNotificationForRequesterAndPendingCandidates() {
        val fixture = createManagerActionFixture(false, true)
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        substituteRequestService.close(fixture.requestId, fixture.managerUserId)
        val request = requestOf(fixture.requestId)
        assertThat(request.status).isEqualTo(RequestStatus.CLOSED)
        assertThat(request.closeReason).isEqualTo(RequestCloseReason.MANAGER_CLOSED)
        assertThat(shiftMemberIdOf(fixture.shiftId)).isEqualTo(fixture.requesterMemberId)
        assertNotifiedOnly(
            notificationsOf(fixture.allMemberIds()),
            listOf(fixture.requesterMemberId, fixture.firstCandidateMemberId),
            NotificationType.SUBSTITUTE_MANAGER_CLOSED,
            MANAGER_CLOSED_MESSAGE,
        )
    }

    @Test
    @DisplayName("ACCEPTED Request를 MANAGER가 종료하면 요청자와 수락자에게만 종료 알림이 저장되고 담당자는 그대로다")
    fun closeAcceptedRequest_savesClosedNotificationForRequesterAndAccepted() {
        val fixture = createManagerActionFixture(true, false)
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        substituteRequestService.close(fixture.requestId, fixture.managerUserId)
        val request = requestOf(fixture.requestId)
        assertThat(request.status).isEqualTo(RequestStatus.CLOSED)
        assertThat(request.closeReason).isEqualTo(RequestCloseReason.MANAGER_CLOSED)
        assertThat(shiftMemberIdOf(fixture.shiftId)).isEqualTo(fixture.requesterMemberId)
        assertNotifiedOnly(
            notificationsOf(fixture.allMemberIds()),
            listOf(fixture.requesterMemberId, fixture.firstCandidateMemberId),
            NotificationType.SUBSTITUTE_MANAGER_CLOSED,
            MANAGER_CLOSED_MESSAGE,
        )
    }

    @Test
    @DisplayName("승인을 실행한 바깥 Transaction이 Rollback되면 Request·Shift는 그대로이고 승인 알림도 저장되지 않는다")
    fun approveInRolledBackTransaction_keepsRequestAndShiftAndSavesNoNotification() {
        val fixture = createManagerActionFixture(true, false)
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            val approved = substituteRequestService.approve(fixture.requestId, fixture.managerUserId)
            assertThat(approved.status).isEqualTo(RequestStatus.APPROVED)
            status.setRollbackOnly()
        }
        val request = requestOf(fixture.requestId)
        assertThat(request.status).isEqualTo(RequestStatus.ACCEPTED)
        assertThat(request.approvedAt).isNull()
        assertThat(shiftMemberIdOf(fixture.shiftId)).isEqualTo(fixture.requesterMemberId)
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
    }

    @Test
    @DisplayName("요청 생성을 실행한 바깥 Transaction이 Rollback되면 Request·Candidate·알림이 남지 않는다")
    fun createInRolledBackTransaction_savesNoRequestCandidateOrNotification() {
        val fixture = createRequestFixture()
        val candidateMemberIds = listOf(fixture.firstCandidateMemberId, fixture.secondCandidateMemberId)
        assertThat(requestsOf(fixture.shiftId)).isEmpty()
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        TransactionTemplate(transactionManager).executeWithoutResult {
            assertThat(entityManager.createQuery(
                "select count(c) from SubstituteCandidate c where c.member.id in :memberIds",
                Long::class.javaObjectType,
            ).setParameter("memberIds", candidateMemberIds).singleResult).isZero()
        }
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            val response = substituteRequestService.create(fixture.shiftId, fixture.requesterUserId)
            assertThat(response.requestCreated).isTrue()
            assertThat(response.requestId).isNotNull()
            assertThat(response.candidateCount).isEqualTo(2)
            entityManager.flush()
            entityManager.clear()
            val requests = entityManager.createQuery(
                "select r from SubstituteRequest r where r.shift.id = :shiftId",
                SubstituteRequest::class.java,
            ).setParameter("shiftId", fixture.shiftId).resultList
            assertThat(requests).hasSize(1)
            assertThat(requests[0].id).isEqualTo(response.requestId)
            assertThat(requests[0].status).isEqualTo(RequestStatus.OPEN)
            assertThat(entityManager.createQuery(
                "select c.member.id from SubstituteCandidate c where c.request.id = :requestId",
                Long::class.javaObjectType,
            ).setParameter("requestId", response.requestId).resultList)
                .containsExactlyInAnyOrderElementsOf(candidateMemberIds)
            status.setRollbackOnly()
        }
        assertThat(requestsOf(fixture.shiftId)).isEmpty()
        TransactionTemplate(transactionManager).executeWithoutResult {
            assertThat(entityManager.createQuery(
                "select count(c) from SubstituteCandidate c where c.member.id in :memberIds",
                Long::class.javaObjectType,
            ).setParameter("memberIds", candidateMemberIds).singleResult).isZero()
        }
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        assertThat(shiftMemberIdOf(fixture.shiftId)).isEqualTo(fixture.requesterMemberId)
    }

    @Test
    @DisplayName("승인 Commit 후 알림 저장이 실패해도 업무는 유지되고 알림은 모두 Rollback된다")
    fun approveWithNotificationSaveFailure_keepsBusinessCommitAndRollsBackNotifications() {
        val fixture = createManagerActionFixture(true, false)
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        assertThat(requestOf(fixture.requestId).status).isEqualTo(RequestStatus.ACCEPTED)
        assertThat(shiftMemberIdOf(fixture.shiftId)).isEqualTo(fixture.requesterMemberId)
        val insertedIds = mutableListOf<Long>()
        val failedRecipients = mutableListOf<Long>()
        val transactionNames = mutableListOf<String?>()

        doAnswer { invocation ->
            val notification = invocation.getArgument<Notification>(0)
            failedRecipients.add(checkNotNull(notification.recipientMember.id))
            transactionNames.add(TransactionSynchronizationManager.getCurrentTransactionName())
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue()
            // Inspect the preceding IDENTITY INSERT through JDBC, without an extra JPA flush/query.
            val insertedId = entityManager.unwrap(Session::class.java).doReturningWork { connection ->
                connection.prepareStatement(
                    "select id from notification where recipient_member_id = ? and type = ?"
                ).use { statement ->
                    statement.setLong(1, fixture.requesterMemberId)
                    statement.setString(2, NotificationType.SUBSTITUTE_APPROVED.name)
                    statement.executeQuery().use { rows ->
                        assertThat(rows.next()).isTrue()
                        val id = rows.getLong(1)
                        assertThat(rows.next()).isFalse()
                        id
                    }
                }
            }
            insertedIds.add(insertedId)
            throw IllegalStateException("J3-09 injected recipient failure")
        }.`when`(notificationRepository).save(argThat<Notification> {
            recipientMember.id == fixture.firstCandidateMemberId &&
                type == NotificationType.SUBSTITUTE_APPROVED
        })

        assertThatCode {
            substituteRequestService.approve(fixture.requestId, fixture.managerUserId)
        }.doesNotThrowAnyException()

        val saved = argumentCaptor<Notification>()
        verify(notificationRepository, times(2)).save(saved.capture())
        assertThat(saved.allValues.map { it.recipientMember.id })
            .containsExactly(fixture.requesterMemberId, fixture.firstCandidateMemberId)
        saved.allValues.forEach {
            assertThat(it.type).isEqualTo(NotificationType.SUBSTITUTE_APPROVED)
        }
        assertThat(failedRecipients).containsExactly(fixture.firstCandidateMemberId)
        assertThat(insertedIds).hasSize(1).doesNotContainNull()
        assertThat(saved.allValues[0].id).isEqualTo(insertedIds[0])
        assertThat(transactionNames).hasSize(1).doesNotContainNull()
        assertThat(transactionNames[0])
            .endsWith("NotificationService.createSubstituteApprovedNotifications")

        val request = requestOf(fixture.requestId)
        assertThat(request.status).isEqualTo(RequestStatus.APPROVED)
        assertThat(request.approvedAt).isNotNull()
        assertThat(shiftMemberIdOf(fixture.shiftId)).isEqualTo(fixture.firstCandidateMemberId)
        assertThat(notificationsOf(fixture.allMemberIds())).isEmpty()
        TransactionTemplate(transactionManager).executeWithoutResult {
            assertThat(entityManager.find(Notification::class.java, insertedIds[0])).isNull()
        }
        println("J3-09 business=APPROVED, shift=accepted, injected=1, first INSERT ID=${insertedIds[0]}, remaining notifications=0")
    }

    private fun assertNotifiedOnly(
        notifications: List<Notification>,
        expectedRecipientMemberIds: List<Long>,
        expectedType: NotificationType,
        expectedMessage: String,
    ) {
        assertThat(notifications.map { it.recipientMember.id })
            .containsExactlyInAnyOrderElementsOf(expectedRecipientMemberIds)
        notifications.forEach {
            assertThat(it.type).isEqualTo(expectedType)
            assertThat(it.message).isEqualTo(expectedMessage)
            assertThat(it.readAt).isNull()
        }
    }

    private fun notificationsOf(memberIds: List<Long>): List<Notification> =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            entityManager.createQuery(
                "select n from Notification n join fetch n.recipientMember rm where rm.id in :memberIds",
                Notification::class.java,
            ).setParameter("memberIds", memberIds).resultList
        })

    private fun requestsOf(shiftId: Long): List<SubstituteRequest> =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            entityManager.createQuery(
                "select r from SubstituteRequest r where r.shift.id = :shiftId",
                SubstituteRequest::class.java,
            ).setParameter("shiftId", shiftId).resultList
        })

    private fun candidateMemberIdsOf(requestId: Long): List<Long> =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            entityManager.createQuery(
                "select c.member.id from SubstituteCandidate c where c.request.id = :requestId",
                Long::class.javaObjectType,
            ).setParameter("requestId", requestId).resultList
        })

    private fun requestStatusOf(requestId: Long): RequestStatus =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            entityManager.find(SubstituteRequest::class.java, requestId).status
        })

    private fun requestOf(requestId: Long): SubstituteRequest =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            entityManager.find(SubstituteRequest::class.java, requestId)
        })

    private fun shiftMemberIdOf(shiftId: Long): Long =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            entityManager.find(Shift::class.java, shiftId).member.id
        })

    private fun candidateStatusOf(candidateId: Long): CandidateStatus =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            entityManager.find(SubstituteCandidate::class.java, candidateId).status
        })

    private fun createNoCandidateFixture(): NoCandidateFixture =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = saveWorkplace(suffix)
            val schedule = savePublishedSchedule(workplace)
            val activeManager = saveMember(workplace, "manager-" + suffix, WorkplaceRole.MANAGER, null)
            val leftManager = saveMember(workplace, "left-manager-" + suffix, WorkplaceRole.MANAGER, now().minusDays(1))
            val requester = saveMember(workplace, "requester-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val shift = saveShift(schedule, requester)
            NoCandidateFixture(
                checkNotNull(shift.id), checkNotNull(requester.user.id), checkNotNull(requester.id),
                checkNotNull(activeManager.id), checkNotNull(leftManager.id),
            )
        })

    private fun createOpenRequestFixture(): OpenRequestFixture =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = saveWorkplace(suffix)
            val schedule = savePublishedSchedule(workplace)
            val firstManager = saveMember(workplace, "manager1-" + suffix, WorkplaceRole.MANAGER, null)
            val secondManager = saveMember(workplace, "manager2-" + suffix, WorkplaceRole.MANAGER, null)
            val leftManager = saveMember(workplace, "left-manager-" + suffix, WorkplaceRole.MANAGER, now().minusDays(1))
            val requester = saveMember(workplace, "requester-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val firstCandidateMember = saveMember(workplace, "candidate1-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val secondCandidateMember = saveMember(workplace, "candidate2-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val shift = saveShift(schedule, requester)
            val request = substituteRequestRepository.save(
                SubstituteRequest(shift, requester, RequestStatus.OPEN),
            )
            val firstCandidate = substituteCandidateRepository.save(
                SubstituteCandidate(request, firstCandidateMember),
            )
            val secondCandidate = substituteCandidateRepository.save(
                SubstituteCandidate(request, secondCandidateMember),
            )
            OpenRequestFixture(
                checkNotNull(request.id), checkNotNull(firstCandidate.id), checkNotNull(secondCandidate.id),
                checkNotNull(firstCandidateMember.user.id), checkNotNull(requester.id),
                checkNotNull(firstManager.id), checkNotNull(secondManager.id), checkNotNull(leftManager.id),
                checkNotNull(firstCandidateMember.id), checkNotNull(secondCandidateMember.id),
            )
        })

    private fun createSingleCandidateRequestFixture(): SingleCandidateRequestFixture =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = saveWorkplace(suffix)
            val schedule = savePublishedSchedule(workplace)
            val activeManager = saveMember(workplace, "manager-" + suffix, WorkplaceRole.MANAGER, null)
            val leftManager = saveMember(workplace, "left-manager-" + suffix, WorkplaceRole.MANAGER, now().minusDays(1))
            val requester = saveMember(workplace, "requester-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val candidateMember = saveMember(workplace, "candidate-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val shift = saveShift(schedule, requester)
            val request = substituteRequestRepository.save(
                SubstituteRequest(shift, requester, RequestStatus.OPEN),
            )
            val candidate = substituteCandidateRepository.save(
                SubstituteCandidate(request, candidateMember),
            )
            SingleCandidateRequestFixture(
                checkNotNull(request.id), checkNotNull(candidate.id), checkNotNull(candidateMember.user.id),
                checkNotNull(requester.id), checkNotNull(activeManager.id), checkNotNull(leftManager.id),
                checkNotNull(candidateMember.id),
            )
        })

    private fun createManagerActionFixture(
        acceptFirst: Boolean,
        rejectSecond: Boolean,
    ): ManagerActionFixture =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = saveWorkplace(suffix)
            val schedule = savePublishedSchedule(workplace)
            val manager = saveMember(workplace, "manager-" + suffix, WorkplaceRole.MANAGER, null)
            val requester = saveMember(workplace, "requester-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val firstCandidateMember = saveMember(workplace, "candidate1-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val secondCandidateMember = saveMember(workplace, "candidate2-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val shift = saveShift(schedule, requester)
            val request = substituteRequestRepository.save(
                SubstituteRequest(shift, requester, RequestStatus.OPEN),
            )
            val firstCandidate = substituteCandidateRepository.save(
                SubstituteCandidate(request, firstCandidateMember),
            )
            val secondCandidate = substituteCandidateRepository.save(
                SubstituteCandidate(request, secondCandidateMember),
            )
            if (acceptFirst) firstCandidate.acceptRequest(now())
            if (rejectSecond) secondCandidate.reject(now())
            ManagerActionFixture(
                checkNotNull(request.id), checkNotNull(shift.id), checkNotNull(manager.user.id),
                checkNotNull(manager.id), checkNotNull(requester.id),
                checkNotNull(firstCandidateMember.id), checkNotNull(secondCandidateMember.id),
            )
        })

    private fun saveWorkplace(suffix: String): Workplace =
        workplaceRepository.save(Workplace("대타 알림 테스트 매장 " + suffix, "SUBN-" + suffix))

    private fun saveShift(schedule: Schedule, member: WorkplaceMember): Shift {
        val startAt = shiftStartAt()
        return shiftRepository.save(Shift(
            schedule, member, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED,
        ))
    }

    private fun createRequestFixture(): RequestFixture =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = workplaceRepository.save(
                Workplace("대타 알림 테스트 매장 " + suffix, "SUBN-" + suffix),
            )
            val schedule = savePublishedSchedule(workplace)
            val manager = saveMember(workplace, "manager-" + suffix, WorkplaceRole.MANAGER, null)
            val requester = saveMember(workplace, "requester-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val firstCandidate = saveMember(workplace, "candidate1-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val secondCandidate = saveMember(workplace, "candidate2-" + suffix, WorkplaceRole.EMPLOYEE, null)
            val leftEmployee = saveMember(workplace, "left-" + suffix, WorkplaceRole.EMPLOYEE, now().minusDays(1))
            val startAt = shiftStartAt()
            val shift = shiftRepository.save(Shift(
                schedule, requester, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED,
            ))
            RequestFixture(
                checkNotNull(shift.id), checkNotNull(requester.user.id), checkNotNull(requester.id),
                checkNotNull(manager.id), checkNotNull(firstCandidate.id), checkNotNull(secondCandidate.id),
                checkNotNull(leftEmployee.id),
            )
        })

    private fun savePublishedSchedule(workplace: Workplace): Schedule {
        val schedule = Schedule(
            workplace,
            shiftStartAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        )
        schedule.publish(now())
        return scheduleRepository.save(schedule)
    }

    private fun saveMember(
        workplace: Workplace,
        name: String,
        role: WorkplaceRole,
        leftAt: LocalDateTime?,
    ): WorkplaceMember {
        val user = userRepository.save(User(name + "@test.com", "password", name))
        return workplaceMemberRepository.save(
            WorkplaceMember(workplace, user, role, now().minusDays(30), leftAt),
        )
    }

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    private fun shiftStartAt(): LocalDateTime =
        now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)

    private fun uniqueSuffix(): String = UUID.randomUUID().toString().substring(0, 8)

    private data class RequestFixture(
        val shiftId: Long,
        val requesterUserId: Long,
        val requesterMemberId: Long,
        val managerMemberId: Long,
        val firstCandidateMemberId: Long,
        val secondCandidateMemberId: Long,
        val leftEmployeeMemberId: Long,
    ) {
        fun allMemberIds(): List<Long> = listOf(
            requesterMemberId, managerMemberId, firstCandidateMemberId,
            secondCandidateMemberId, leftEmployeeMemberId,
        )
    }

    private data class NoCandidateFixture(
        val shiftId: Long,
        val requesterUserId: Long,
        val requesterMemberId: Long,
        val activeManagerMemberId: Long,
        val leftManagerMemberId: Long,
    ) {
        fun allMemberIds(): List<Long> =
            listOf(requesterMemberId, activeManagerMemberId, leftManagerMemberId)
    }

    private data class ManagerActionFixture(
        val requestId: Long,
        val shiftId: Long,
        val managerUserId: Long,
        val managerMemberId: Long,
        val requesterMemberId: Long,
        val firstCandidateMemberId: Long,
        val secondCandidateMemberId: Long,
    ) {
        fun allMemberIds(): List<Long> = listOf(
            managerMemberId, requesterMemberId, firstCandidateMemberId, secondCandidateMemberId,
        )
    }

    private data class SingleCandidateRequestFixture(
        val requestId: Long,
        val candidateId: Long,
        val candidateUserId: Long,
        val requesterMemberId: Long,
        val activeManagerMemberId: Long,
        val leftManagerMemberId: Long,
        val candidateMemberId: Long,
    ) {
        fun allMemberIds(): List<Long> = listOf(
            requesterMemberId, activeManagerMemberId, leftManagerMemberId, candidateMemberId,
        )
    }

    private data class OpenRequestFixture(
        val requestId: Long,
        val firstCandidateId: Long,
        val secondCandidateId: Long,
        val firstCandidateUserId: Long,
        val requesterMemberId: Long,
        val firstManagerMemberId: Long,
        val secondManagerMemberId: Long,
        val leftManagerMemberId: Long,
        val firstCandidateMemberId: Long,
        val secondCandidateMemberId: Long,
    ) {
        fun allMemberIds(): List<Long> = listOf(
            requesterMemberId, firstManagerMemberId, secondManagerMemberId,
            leftManagerMemberId, firstCandidateMemberId, secondCandidateMemberId,
        )
    }

    private companion object {
        private val FIXED_NOW = Instant.parse("2026-09-15T01:00:00Z")
        private const val REQUEST_RECEIVED_MESSAGE = "새로운 대타 요청이 도착했습니다."
        private const val NO_CANDIDATE_MESSAGE = "대체 근무 가능한 후보가 없습니다."
        private const val ACCEPTED_MESSAGE = "대체 근무 수락자가 생겼습니다. 최종 승인이 필요합니다."
        private const val ALL_REJECTED_MESSAGE = "대체 근무 요청을 받은 모든 수신자가 거절했습니다."
        private const val APPROVED_REQUESTER_MESSAGE = "요청한 대체 근무 변경이 최종 승인되었습니다."
        private const val APPROVED_ACCEPTED_MESSAGE = "수락한 대체 근무가 공식 근무로 확정되었습니다."
        private const val MANAGER_CLOSED_MESSAGE = "관리자가 대체 근무 요청을 종료했습니다."
    }
}
