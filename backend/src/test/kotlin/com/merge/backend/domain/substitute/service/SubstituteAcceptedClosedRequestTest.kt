package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.substitute.dto.AcceptedClosedSubstituteRequestResponse
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

// 종료된 대타 조회 (API-SB-01, G03-D-22 §6). 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class SubstituteAcceptedClosedRequestTest {

    @Autowired
    private lateinit var substituteRequestService: SubstituteRequestService

    @Autowired
    private lateinit var substituteCandidateService: SubstituteCandidateService

    @Autowired
    private lateinit var substituteRequestRepository: SubstituteRequestRepository

    @Autowired
    private lateinit var substituteCandidateRepository: SubstituteCandidateRepository

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
    @DisplayName("관리자가 종료한 요청은 MANAGER_CLOSED로 보이고 종료 시각은 DB closedAt이다")
    fun managerClosedRequestIsListed() {
        // given: 수락 뒤 한 시간 지나 관리자 종료
        val f = createAcceptedRequest(daysUntilShift = 7)
        moveClock(hours = 1)
        substituteRequestService.close(f.requestId, f.managerUserId)

        // when
        val responses = closedRequestsOf(f.accepterUserId)

        // then
        assertThat(responses).hasSize(1)
        val response = responses[0]
        assertThat(response.requestId).isEqualTo(f.requestId)
        assertThat(response.requestStatus).isEqualTo(RequestStatus.CLOSED)
        assertThat(response.closeReason).isEqualTo(RequestCloseReason.MANAGER_CLOSED)
        assertThat(response.candidateStatus).isEqualTo(CandidateStatus.ACCEPTED)
        assertThat(response.closedAt).isEqualTo(storedClosedAt(f.requestId)).isEqualTo(now())
    }

    @Test
    @DisplayName("승인 전에 근무가 시작되면(시작 시각 포함) 계산된 만료(CLOSED/EXPIRED)로 보이고 종료 시각은 근무 시작 시각이다")
    fun expiredRequestIsListedWithShiftStart() {
        // given
        val f = createAcceptedRequest(daysUntilShift = 1)

        // when & then: 근무 시작 1초 전은 아직 진행 중, 정확히 시작 시각부터 만료 (G03-D-19 D19-4)
        testClock.setInstant(f.startAt.minusSeconds(1).atZone(testClock.zone).toInstant())
        assertThat(closedRequestsOf(f.accepterUserId)).isEmpty()
        testClock.setInstant(f.startAt.atZone(testClock.zone).toInstant())
        assertThat(closedRequestsOf(f.accepterUserId).map { it.closeReason }).containsExactly(RequestCloseReason.EXPIRED)

        // when: 근무 시작 한 시간 뒤 조회
        testClock.setInstant(f.startAt.plusHours(1).atZone(testClock.zone).toInstant())
        val responses = closedRequestsOf(f.accepterUserId)

        // then
        assertThat(responses).hasSize(1)
        assertThat(responses[0].requestStatus).isEqualTo(RequestStatus.CLOSED)
        assertThat(responses[0].closeReason).isEqualTo(RequestCloseReason.EXPIRED)
        assertThat(responses[0].closedAt).isEqualTo(f.startAt)
        assertThat(storedStatus(f.requestId)).isEqualTo(RequestStatus.ACCEPTED)
    }

    @Test
    @DisplayName("승인된 요청은 승인 당일 보이고 8일 뒤에는 근무 전이어도 보이지 않는다")
    fun approvedRequestIsListedForSevenDays() {
        // given: 근무는 3주 뒤, 오늘 승인
        val f = createAcceptedRequest(daysUntilShift = 21)
        substituteRequestService.approve(f.requestId, f.managerUserId)

        // when & then: 오늘
        val today = closedRequestsOf(f.accepterUserId)
        assertThat(today).hasSize(1)
        assertThat(today[0].requestStatus).isEqualTo(RequestStatus.APPROVED)
        assertThat(today[0].closeReason).isNull()
        assertThat(today[0].closedAt).isEqualTo(now())

        // when & then: 8일 뒤
        moveClock(days = 8)
        assertThat(closedRequestsOf(f.accepterUserId)).isEmpty()
    }

    @Test
    @DisplayName("승인 뒤 근무 담당자가 바뀌어도 승인 기록은 그대로이고 응답에 현재 담당자 정보가 없다")
    fun approvedRequestHidesCurrentAssignee() {
        // given: 승인 뒤 관리자가 다른 직원에게 담당자 변경 (V3 인계 Command 대신 Shift 직접 변경으로 흉내)
        val f = createAcceptedRequest(daysUntilShift = 21)
        substituteRequestService.approve(f.requestId, f.managerUserId)
        inTransaction {
            shiftRepository.findByIdForUpdate(f.shiftId).orElseThrow()
                .changeMember(workplaceMemberRepository.findById(f.otherCandidateMemberId).orElseThrow())
        }

        // when
        val responses = closedRequestsOf(f.accepterUserId)

        // then
        assertThat(responses).hasSize(1)
        assertThat(responses[0].requestStatus).isEqualTo(RequestStatus.APPROVED)
        assertThat(responses[0].requesterName).isEqualTo(f.requesterName)
        assertOnlyAllowedFields()
    }

    @Test
    @DisplayName("종료 후 7일까지는 보이고 그 뒤에는 보이지 않으며 DB Row는 그대로다")
    fun closedRequestDisappearsAfterSevenDays() {
        // given
        val f = createAcceptedRequest(daysUntilShift = 21)
        substituteRequestService.close(f.requestId, f.managerUserId)
        val closedAt = now()

        // when & then: 정확히 7일 뒤 (경계 포함)
        testClock.setInstant(closedAt.plusDays(7).atZone(testClock.zone).toInstant())
        assertThat(closedRequestsOf(f.accepterUserId)).hasSize(1)

        // when & then: 7일 + 1초 뒤, 8일 뒤
        testClock.setInstant(closedAt.plusDays(7).plusSeconds(1).atZone(testClock.zone).toInstant())
        assertThat(closedRequestsOf(f.accepterUserId)).isEmpty()
        moveClock(days = 1)
        assertThat(closedRequestsOf(f.accepterUserId)).isEmpty()

        // then: 조회는 DB를 바꾸지 않음
        assertThat(storedStatus(f.requestId)).isEqualTo(RequestStatus.CLOSED)
        assertThat(storedClosedAt(f.requestId)).isEqualTo(closedAt)
    }

    @Test
    @DisplayName("종료된 요청에 수락·거절을 다시 시도하면 기존처럼 거부되고 상태는 그대로다")
    fun respondingToClosedRequestIsRejected() {
        // given
        val f = createAcceptedRequest(daysUntilShift = 7)
        substituteRequestService.close(f.requestId, f.managerUserId)

        // when & then: 수락했던 후보 / 대기 후보
        assertBusinessError(SubstituteRequestErrorCode.CANDIDATE_ALREADY_RESPONDED) {
            substituteCandidateService.acceptCandidate(f.accepterCandidateId, f.accepterUserId)
        }
        assertBusinessError(SubstituteRequestErrorCode.REQUEST_NOT_OPEN) {
            substituteCandidateService.acceptCandidate(f.otherCandidateId, f.otherCandidateUserId)
        }
        assertBusinessError(SubstituteRequestErrorCode.REQUEST_NOT_OPEN) {
            substituteCandidateService.rejectCandidate(f.otherCandidateId, f.otherCandidateUserId)
        }

        // then
        assertThat(storedStatus(f.requestId)).isEqualTo(RequestStatus.CLOSED)
        inTransaction {
            assertThat(candidateStatus(f.accepterCandidateId)).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(candidateStatus(f.otherCandidateId)).isEqualTo(CandidateStatus.PENDING)
        }
        assertThat(closedRequestsOf(f.accepterUserId)).hasSize(1)
    }

    @Test
    @DisplayName("수락하지 않은 대기 후보에게는 종료된 요청이 보이지 않는다")
    fun pendingCandidateSeesNothing() {
        // given
        val f = createAcceptedRequest(daysUntilShift = 7)
        substituteRequestService.close(f.requestId, f.managerUserId)

        // when & then
        assertThat(closedRequestsOf(f.otherCandidateUserId)).isEmpty()
    }

    @Test
    @DisplayName("근무가 취소·인계되어 종료된 요청은 SHIFT_CHANGED로 보인다")
    fun shiftChangedRequestIsListed() {
        // given: V3 Command 대신 Shift를 잠근 Transaction 안에서 종료 함수 호출
        val f = createAcceptedRequest(daysUntilShift = 7)
        inTransaction {
            shiftRepository.findByIdForUpdate(f.shiftId).orElseThrow()
            substituteRequestService.closeActiveRequestsByShiftChange(f.shiftId, now())
        }

        // when
        val responses = closedRequestsOf(f.accepterUserId)

        // then
        assertThat(responses).hasSize(1)
        assertThat(responses[0].requestStatus).isEqualTo(RequestStatus.CLOSED)
        assertThat(responses[0].closeReason).isEqualTo(RequestCloseReason.SHIFT_CHANGED)
        assertThat(responses[0].closedAt).isEqualTo(now())
    }

    @Test
    @DisplayName("내가 수락한 요청만 효력 종료 시각 최신순으로 보이고 다른 후보 정보는 없다")
    fun onlyMyAcceptedRequestsInLatestOrder() {
        // given: 같은 직원이 수락한 요청 2건(종료 시각 다름) + 다른 직원이 수락한 요청 1건
        val first = createAcceptedRequest(daysUntilShift = 7)
        val second = createAcceptedRequest(daysUntilShift = 14, accepter = first.accepter)
        val others = createAcceptedRequest(daysUntilShift = 7)
        substituteRequestService.close(first.requestId, first.managerUserId)
        moveClock(hours = 1)
        substituteRequestService.close(second.requestId, second.managerUserId)
        substituteRequestService.close(others.requestId, others.managerUserId)

        // when
        val responses = closedRequestsOf(first.accepterUserId)

        // then
        assertThat(responses.map { it.requestId }).containsExactly(second.requestId, first.requestId)
        assertOnlyAllowedFields()
    }

    @Test
    @DisplayName("수락한 뒤 그 근무지를 퇴사한 소속의 기록은 보이지 않는다")
    fun endedMembershipRecordsAreHidden() {
        // given: 수락 → 관리자 종료 → 수락자 소속 종료 (퇴사 Command가 없어 leftAt 직접 기록)
        val f = createAcceptedRequest(daysUntilShift = 7)
        substituteRequestService.close(f.requestId, f.managerUserId)
        assertThat(closedRequestsOf(f.accepterUserId)).hasSize(1)

        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), f.accepterMemberId)

        // when & then
        assertThat(closedRequestsOf(f.accepterUserId)).isEmpty()
    }

    @Test
    @DisplayName("퇴사 후 같은 근무지에 재가입하면 이전 소속 기간의 기록은 보이지 않고 새 소속의 기록만 보인다")
    fun rejoinedMemberSeesOnlyCurrentMembershipRecords() {
        // given: 이전 소속으로 수락 → 관리자 종료 → 퇴사 → 같은 근무지 재가입
        val old = createAcceptedRequest(daysUntilShift = 7)
        substituteRequestService.close(old.requestId, old.managerUserId)
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), old.accepterMemberId)
        val rejoined = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val oldMember = workplaceMemberRepository.findById(old.accepterMemberId).orElseThrow()
                workplaceMemberRepository.save(
                    WorkplaceMember(oldMember.workplace, oldMember.user, WorkplaceRole.EMPLOYEE, now())
                )
            }
        )

        // when & then: 이전 소속 기록 0건
        assertThat(closedRequestsOf(old.accepterUserId)).isEmpty()

        // when & then: 새 소속으로 수락·종료한 기록만 보임
        val current = createAcceptedRequest(daysUntilShift = 14, accepter = rejoined)
        moveClock(hours = 1)
        substituteRequestService.close(current.requestId, current.managerUserId)
        assertThat(closedRequestsOf(old.accepterUserId).map { it.requestId }).containsExactly(current.requestId)
    }

    // ===================== 실행 헬퍼 =====================

    private fun closedRequestsOf(userId: Long): List<AcceptedClosedSubstituteRequestResponse> =
        substituteCandidateService.findAcceptedClosedRequests(userId)

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    private fun moveClock(days: Long = 0, hours: Long = 0) {
        testClock.setInstant(testClock.instant().plusSeconds(days * 86_400 + hours * 3_600))
    }

    private fun inTransaction(block: () -> Unit) {
        TransactionTemplate(transactionManager).executeWithoutResult { block() }
    }

    // ===================== 검증 헬퍼 =====================

    private fun storedStatus(requestId: Long): RequestStatus = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            substituteRequestRepository.findById(requestId).orElseThrow().status
        }
    )

    private fun storedClosedAt(requestId: Long): LocalDateTime? =
        TransactionTemplate(transactionManager).execute {
            substituteRequestRepository.findById(requestId).orElseThrow().closedAt
        }

    private fun candidateStatus(candidateId: Long): CandidateStatus =
        substituteCandidateRepository.findById(candidateId).orElseThrow().status

    // 현재 근무 담당자·다른 후보 정보가 응답에 없음 (API-CATALOG API-SB-01 "넣지 않는다")
    private fun assertOnlyAllowedFields() {
        assertThat(AcceptedClosedSubstituteRequestResponse::class.java.recordComponents.map { it.name })
            .containsExactlyInAnyOrder(
                "requestId", "workplaceId", "workplaceName", "requesterName", "startAt", "endAt",
                "requestStatus", "closeReason", "candidateStatus", "closedAt",
            )
    }

    private fun assertBusinessError(expected: SubstituteRequestErrorCode, action: () -> Unit) {
        assertThatThrownBy { action() }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(expected)
    }

    // ===================== Fixture =====================

    private data class Fixture(
        val managerUserId: Long,
        val requesterName: String,
        val accepter: WorkplaceMember,
        val accepterUserId: Long,
        val accepterMemberId: Long,
        val accepterCandidateId: Long,
        val otherCandidateUserId: Long,
        val otherCandidateMemberId: Long,
        val otherCandidateId: Long,
        val shiftId: Long,
        val requestId: Long,
        val startAt: LocalDateTime,
    )

    // 관리자·요청자·후보 2명, 요청자의 근무 1개에 요청 1건. 첫 후보가 실제 수락 흐름으로 ACCEPTED
    // accepter를 주면 그 직원이 같은 근무지에서 또 수락한다
    private fun createAcceptedRequest(daysUntilShift: Long, accepter: WorkplaceMember? = null): Fixture {
        val f = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val suffix = uniqueSuffix()
                val workplace = accepter?.let { workplaceRepository.findById(requireNotNull(it.workplace.id)).orElseThrow() }
                    ?: workplaceRepository.save(Workplace("종료 대타 조회 테스트 매장 $suffix", "ACL-$suffix"))
                val startAt = now().plusDays(daysUntilShift).withHour(10).withMinute(0).withSecond(0).withNano(0)
                val schedule = savePublishedSchedule(workplace, startAt)

                val manager = saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
                val requester = saveMember(workplace, "requester-$suffix", WorkplaceRole.EMPLOYEE)
                val accepterMember = accepter?.let { workplaceMemberRepository.findById(requireNotNull(it.id)).orElseThrow() }
                    ?: saveMember(workplace, "accepter-$suffix", WorkplaceRole.EMPLOYEE)
                val other = saveMember(workplace, "other-$suffix", WorkplaceRole.EMPLOYEE)

                val shift = shiftRepository.save(Shift(schedule, requester, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))
                val request = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))
                val accepterCandidate = substituteCandidateRepository.save(SubstituteCandidate(request, accepterMember))
                val otherCandidate = substituteCandidateRepository.save(SubstituteCandidate(request, other))

                Fixture(
                    managerUserId = requireNotNull(manager.user.id),
                    requesterName = requester.user.name,
                    accepter = accepterMember,
                    accepterUserId = requireNotNull(accepterMember.user.id),
                    accepterMemberId = requireNotNull(accepterMember.id),
                    accepterCandidateId = requireNotNull(accepterCandidate.id),
                    otherCandidateUserId = requireNotNull(other.user.id),
                    otherCandidateMemberId = requireNotNull(other.id),
                    otherCandidateId = requireNotNull(otherCandidate.id),
                    shiftId = requireNotNull(shift.id),
                    requestId = requireNotNull(request.id),
                    startAt = startAt,
                )
            }
        )

        // 실제 수락 흐름으로 ACCEPTED를 만든다
        substituteCandidateService.acceptCandidate(f.accepterCandidateId, f.accepterUserId)

        return f
    }

    private fun savePublishedSchedule(workplace: Workplace, startAt: LocalDateTime): Schedule {
        val weekStart = startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // 같은 근무지·같은 주 근무표는 하나뿐 (Unique)
        return scheduleRepository.findByWorkplace_IdAndWeekStartDate(workplace.id, weekStart).orElse(null)
            ?: scheduleRepository.save(Schedule(workplace, weekStart).also { it.publish(now()) })
    }

    private fun saveMember(workplace: Workplace, name: String, role: WorkplaceRole): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, now().minusDays(30)))
    }

    companion object {
        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")

        private fun uniqueSuffix(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}
