package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftCancellationReason
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.substitute.dto.ClosedSubstituteRequestPageResponse
import com.merge.backend.domain.substitute.dto.ClosedSubstituteRequestResponse
import com.merge.backend.domain.substitute.dto.ClosedSubstituteRequestResponse.Outcome
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.ErrorCode
import com.merge.backend.global.exception.GlobalErrorCode
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

// 관리자 끝난 대타 요청 조회 (API-SB-02, V4-N-49~54). 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class SubstituteClosedRequestTest {

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

    // V4-N-49
    @Test
    @DisplayName("그 근무지의 현재 관리자만 조회할 수 있고 직원·비구성원·퇴사한 관리자·없는 근무지는 403이다")
    fun onlyCurrentManagerCanList() {
        // given
        val place = newPlace()
        val r = createRequest(place, daysUntilShift = 7)
        substituteRequestService.close(r.requestId, place.managerUserId)
        val outsider = newPlace()
        val leftManager = addMember(place, WorkplaceRole.MANAGER)
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), leftManager.memberId)

        // when & then
        assertThat(closedOf(place).items.map { it.requestId }).containsExactly(r.requestId)
        assertError(WorkplaceErrorCode.MANAGER_REQUIRED) { closedOf(place, actorUserId = r.requesterUserId) }
        assertError(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER) { closedOf(place, actorUserId = outsider.managerUserId) }
        assertError(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER) { closedOf(place, actorUserId = leftManager.userId) }
        assertError(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER) {
            substituteRequestService.findClosedRequests(Long.MAX_VALUE, place.managerUserId, null, null, 0, 20)
        }
    }

    // V4-N-49
    @Test
    @DisplayName("다른 근무지의 끝난 요청은 보이지 않는다")
    fun otherWorkplaceRequestsAreHidden() {
        // given
        val mine = newPlace()
        val others = newPlace()
        val myRequest = createRequest(mine, daysUntilShift = 7)
        val otherRequest = createRequest(others, daysUntilShift = 7)
        substituteRequestService.close(myRequest.requestId, mine.managerUserId)
        substituteRequestService.close(otherRequest.requestId, others.managerUserId)

        // when & then
        assertThat(closedOf(mine).items.map { it.requestId }).containsExactly(myRequest.requestId)
        assertThat(closedOf(others).items.map { it.requestId }).containsExactly(otherRequest.requestId)
    }

    // V4-N-50
    @Test
    @DisplayName("CLOSED는 모든 사유가 보이고 승인된 요청과 진행 중 요청은 보이지 않는다")
    fun allCloseReasonsListedWithoutApprovedAndActive() {
        // given
        val place = newPlace()
        val rejected = createRequest(place, daysUntilShift = 2, accept = false)
        substituteCandidateService.rejectCandidate(rejected.accepterCandidateId, rejected.accepterUserId)
        substituteCandidateService.rejectCandidate(rejected.otherCandidateId, rejected.otherCandidateUserId)

        val managerClosed = createRequest(place, daysUntilShift = 3)
        substituteRequestService.close(managerClosed.requestId, place.managerUserId)

        // V3 취소 Command 대신 Shift를 잠근 Transaction 안에서 취소 + 종료 함수 호출
        val shiftChanged = createRequest(place, daysUntilShift = 4)
        inTransaction {
            shiftRepository.findByIdForUpdate(shiftChanged.shiftId).orElseThrow()
                .cancel(ShiftCancellationReason.MANAGER_CANCELLED, now(), null)
            substituteRequestService.closeActiveRequestsByShiftChange(shiftChanged.shiftId, now())
        }

        // 퇴사 확정 Command 대신 같은 Transaction에서 leftAt 기록 + 후보 정리 함수 호출
        val ineligible = createRequest(place, daysUntilShift = 5)
        inTransaction {
            jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), ineligible.accepterMemberId)
            substituteCandidateService.closeCandidaciesByMemberLeave(ineligible.accepterMemberId, now())
        }

        val approved = createRequest(place, daysUntilShift = 6)
        substituteRequestService.approve(approved.requestId, place.managerUserId)
        val active = createRequest(place, daysUntilShift = 7)

        // when
        val items = closedOf(place).items.associateBy { it.requestId }

        // then
        assertThat(items.keys).containsExactlyInAnyOrder(
            rejected.requestId, managerClosed.requestId, shiftChanged.requestId, ineligible.requestId,
        )
        assertThat(items.values.map { it.outcome }).containsOnly(Outcome.CLOSED)
        assertThat(items.getValue(rejected.requestId).closeReason).isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED)
        assertThat(items.getValue(rejected.requestId).accepterName).isNull()
        assertThat(items.getValue(managerClosed.requestId).closeReason).isEqualTo(RequestCloseReason.MANAGER_CLOSED)
        assertThat(items.getValue(managerClosed.requestId).accepterName).isEqualTo(managerClosed.accepterName)
        assertThat(items.getValue(shiftChanged.requestId).closeReason).isEqualTo(RequestCloseReason.SHIFT_CHANGED)
        assertThat(items.getValue(shiftChanged.requestId).shiftStatus).isEqualTo(ShiftStatus.CANCELLED)
        assertThat(items.getValue(ineligible.requestId).closeReason).isEqualTo(RequestCloseReason.CANDIDATE_INELIGIBLE)
        assertThat(items.getValue(ineligible.requestId).accepterName).isEqualTo(ineligible.accepterName)
        assertThat(items.values.map { it.closedAt }).containsOnly(now())
        assertThat(storedStatus(approved.requestId)).isEqualTo(RequestStatus.APPROVED)
        assertThat(storedStatus(active.requestId)).isEqualTo(RequestStatus.ACCEPTED)
    }

    // V4-N-50
    @Test
    @DisplayName("승인 전에 근무가 시작되면(시작 시각 포함) 만료로 보이고 진행 중 목록과 겹치지 않으며 DB는 그대로다")
    fun expiredRequestSplitsExactlyFromOngoingList() {
        // given: 내일 근무 수락 대기, 다음 주 근무 수락 대기, 관리자 종료 1건
        val place = newPlace()
        val expiring = createRequest(place, daysUntilShift = 1)
        val active = createRequest(place, daysUntilShift = 7)
        val closed = createRequest(place, daysUntilShift = 7)
        substituteRequestService.close(closed.requestId, place.managerUserId)
        val all = setOf(expiring.requestId, active.requestId, closed.requestId)

        // when & then: 근무 시작 1초 전은 진행 중
        testClock.setInstant(expiring.startAt.minusSeconds(1).atZone(testClock.zone).toInstant())
        assertThat(closedOf(place).items.map { it.requestId }).containsExactly(closed.requestId)
        assertSplit(place, all)

        // when & then: 정확히 시작 시각부터 만료 (G03-D-19 D19-4, SUB-06 startAt > now와 정확히 나뉨)
        testClock.setInstant(expiring.startAt.atZone(testClock.zone).toInstant())
        val expired = closedOf(place).items.single { it.requestId == expiring.requestId }
        assertThat(expired.outcome).isEqualTo(Outcome.EXPIRED)
        assertThat(expired.closeReason).isEqualTo(RequestCloseReason.EXPIRED)
        assertThat(expired.closedAt).isEqualTo(expiring.startAt)
        assertThat(expired.accepterName).isEqualTo(expiring.accepterName)
        assertSplit(place, all)

        // then: 조회는 계산된 만료를 DB에 저장하지 않음
        assertThat(storedStatus(expiring.requestId)).isEqualTo(RequestStatus.ACCEPTED)
        assertThat(storedClosedAt(expiring.requestId)).isNull()
    }

    // V4-N-51
    @Test
    @DisplayName("기본 기간은 효력 종료 시각 기준 최근 7일(경계 포함)이고 from·to로 그 밖도 조회한다")
    fun defaultSevenDaysAndCustomPeriod() {
        // given
        val place = newPlace()
        val r = createRequest(place, daysUntilShift = 21)
        substituteRequestService.close(r.requestId, place.managerUserId)
        val closedAt = now()

        // when & then: 정확히 7일 뒤 (경계 포함), 7일 + 1초 뒤
        testClock.setInstant(closedAt.plusDays(7).atZone(testClock.zone).toInstant())
        assertThat(closedOf(place).items).hasSize(1)
        testClock.setInstant(closedAt.plusDays(7).plusSeconds(1).atZone(testClock.zone).toInstant())
        assertThat(closedOf(place).items).isEmpty()

        // when & then: 8일 뒤에도 from으로 조회 (7일은 보존 기간이 아님), from·to 모두 경계 포함
        moveClock(days = 1)
        assertThat(closedOf(place, from = closedAt).items.map { it.requestId }).containsExactly(r.requestId)
        assertThat(closedOf(place, from = closedAt.minusDays(1), to = closedAt).items).hasSize(1)
        assertThat(closedOf(place, from = closedAt.minusDays(1), to = closedAt.minusSeconds(1)).items).isEmpty()
        assertThat(closedOf(place, from = closedAt.plusSeconds(1)).items).isEmpty()

        // then: DB Row는 그대로
        assertThat(storedStatus(r.requestId)).isEqualTo(RequestStatus.CLOSED)
        assertThat(storedClosedAt(r.requestId)).isEqualTo(closedAt)
    }

    // V4-N-51
    @Test
    @DisplayName("페이지는 size만큼 자르고 다음 페이지 여부를 알려 준다")
    fun pageBoundary() {
        // given: 한 시간 간격으로 3건 종료
        val place = newPlace()
        val requests = (1..3).map { createRequest(place, daysUntilShift = 7L + it) }
        requests.forEach {
            substituteRequestService.close(it.requestId, place.managerUserId)
            moveClock(hours = 1)
        }

        // when
        val first = closedOf(place, page = 0, size = 2)
        val second = closedOf(place, page = 1, size = 2)
        val third = closedOf(place, page = 2, size = 2)

        // then: 최신 종료부터
        assertThat(first.items.map { it.requestId }).containsExactly(requests[2].requestId, requests[1].requestId)
        assertThat(first.hasNext).isTrue()
        assertThat(first.page).isEqualTo(0)
        assertThat(first.size).isEqualTo(2)
        assertThat(second.items.map { it.requestId }).containsExactly(requests[0].requestId)
        assertThat(second.hasNext).isFalse()
        assertThat(third.items).isEmpty()
        assertThat(third.hasNext).isFalse()
        assertThat(closedOf(place, size = 3).hasNext).isFalse()
    }

    // V4-N-51
    @Test
    @DisplayName("기간 순서가 거꾸로이거나 page·size가 범위 밖이면 400 COM-003이고, 권한 검사가 먼저다")
    fun invalidParametersAreRejected() {
        // given
        val place = newPlace()
        val r = createRequest(place, daysUntilShift = 7)
        substituteRequestService.close(r.requestId, place.managerUserId)

        // when & then
        assertError(GlobalErrorCode.INVALID_REQUEST_PARAMETER) { closedOf(place, from = now(), to = now().minusSeconds(1)) }
        assertError(GlobalErrorCode.INVALID_REQUEST_PARAMETER) { closedOf(place, page = -1) }
        assertError(GlobalErrorCode.INVALID_REQUEST_PARAMETER) { closedOf(place, size = 0) }
        assertError(GlobalErrorCode.INVALID_REQUEST_PARAMETER) { closedOf(place, size = 101) }
        assertThat(closedOf(place, size = 100).items).hasSize(1)
        assertThat(closedOf(place, from = now(), to = now()).items).hasSize(1)

        // 직원이 잘못된 값을 보내도 권한 오류가 먼저
        assertError(WorkplaceErrorCode.MANAGER_REQUIRED) { closedOf(place, actorUserId = r.requesterUserId, size = 0) }
    }

    // V4-N-52
    @Test
    @DisplayName("requesterStillAssigned는 지금 근무 담당이 요청 당시 요청자 소속인지를 나타낸다")
    fun requesterStillAssignedFact() {
        // given
        val place = newPlace()
        val r = createRequest(place, daysUntilShift = 7)
        substituteRequestService.close(r.requestId, place.managerUserId)

        // when & then: 그대로
        assertThat(closedOf(place).items.single().requesterStillAssigned).isTrue()

        // when & then: 관리자가 다른 직원에게 담당자 변경 (V3 인계 Command 대신 Shift 직접 변경으로 흉내)
        changeShiftMember(r.shiftId, r.otherCandidateMemberId)
        assertThat(closedOf(place).items.single().requesterStillAssigned).isFalse()
    }

    // V4-N-52
    @Test
    @DisplayName("shiftStarted는 근무 시작 시각부터 참이다")
    fun shiftStartedFact() {
        // given
        val place = newPlace()
        val r = createRequest(place, daysUntilShift = 1)
        substituteRequestService.close(r.requestId, place.managerUserId)

        // when & then
        assertThat(closedOf(place).items.single().shiftStarted).isFalse()
        testClock.setInstant(r.startAt.minusSeconds(1).atZone(testClock.zone).toInstant())
        assertThat(closedOf(place).items.single().shiftStarted).isFalse()
        testClock.setInstant(r.startAt.atZone(testClock.zone).toInstant())
        assertThat(closedOf(place).items.single().shiftStarted).isTrue()
    }

    // V4-N-52
    @Test
    @DisplayName("hasNewerActiveRequest는 같은 근무에 더 최근의 진행 중 요청이 있을 때만 참이다")
    fun hasNewerActiveRequestFact() {
        // given: 첫 요청 종료
        val place = newPlace()
        val first = createRequest(place, daysUntilShift = 1)
        substituteRequestService.close(first.requestId, place.managerUserId)
        assertThat(closedOf(place).items.single().hasNewerActiveRequest).isFalse()

        // when & then: 같은 근무에 새 요청 진행 중 → 참, 새 요청은 끝난 목록에 없음
        val second = addRequestOnShift(place, first)
        assertThat(closedOf(place).items.map { it.requestId to it.hasNewerActiveRequest })
            .containsExactly(first.requestId to true)

        // when & then: 새 요청도 종료 → 둘 다 거짓
        moveClock(hours = 1)
        substituteRequestService.close(second, place.managerUserId)
        assertThat(closedOf(place).items.map { it.requestId to it.hasNewerActiveRequest })
            .containsExactly(second to false, first.requestId to false)

        // when & then: 세 번째 요청이 승인 전에 근무가 시작되면 진행 중이 아님 → 거짓, 세 번째는 만료로 보임
        val third = addRequestOnShift(place, first)
        assertThat(closedOf(place).items.single { it.requestId == first.requestId }.hasNewerActiveRequest).isTrue()
        testClock.setInstant(first.startAt.atZone(testClock.zone).toInstant())
        val items = closedOf(place).items
        assertThat(items.map { it.hasNewerActiveRequest }).containsOnly(false)
        assertThat(items.single { it.requestId == third }.outcome).isEqualTo(Outcome.EXPIRED)
    }

    // V4-N-53
    @Test
    @DisplayName("요청자가 퇴사하면 requesterMembershipEnded가 참이고, 재가입해도 새 소속과 합치지 않는다")
    fun requesterMembershipBoundary() {
        // given
        val place = newPlace()
        val r = createRequest(place, daysUntilShift = 7)
        substituteRequestService.close(r.requestId, place.managerUserId)
        assertThat(closedOf(place).items.single().requesterMembershipEnded).isFalse()

        // when & then: 퇴사 (퇴사 Command가 없어 leftAt 직접 기록)
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), r.requesterMemberId)
        val afterLeave = closedOf(place).items.single()
        assertThat(afterLeave.requesterMembershipEnded).isTrue()
        assertThat(afterLeave.requesterName).isEqualTo(r.requesterName)

        // when & then: 같은 근무지에 재가입하고 새 소속이 그 근무를 맡아도 요청 당시 소속과 다른 Row
        val rejoinedMemberId = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val oldMember = workplaceMemberRepository.findById(r.requesterMemberId).orElseThrow()
                workplaceMemberRepository.save(
                    WorkplaceMember(oldMember.workplace, oldMember.user, WorkplaceRole.EMPLOYEE, now())
                ).id
            }
        )
        changeShiftMember(r.shiftId, rejoinedMemberId)
        val afterRejoin = closedOf(place).items.single()
        assertThat(afterRejoin.requesterMembershipEnded).isTrue()
        assertThat(afterRejoin.requesterStillAssigned).isFalse()
    }

    // V4-N-53
    @Test
    @DisplayName("응답에는 정해진 Field만 있고 User ID·소속 ID·거절 후보·근무지 정보는 없다")
    fun onlyAllowedFields() {
        // given: 다른 후보가 거절한 뒤 수락 → 관리자 종료
        val place = newPlace()
        val r = createRequest(place, daysUntilShift = 7, accept = false)
        substituteCandidateService.rejectCandidate(r.otherCandidateId, r.otherCandidateUserId)
        substituteCandidateService.acceptCandidate(r.accepterCandidateId, r.accepterUserId)
        substituteRequestService.close(r.requestId, place.managerUserId)

        // when
        val page = closedOf(place)

        // then
        assertThat(page.toString()).doesNotContain(r.otherCandidateName)
        assertThat(page.items.single().accepterName).isEqualTo(r.accepterName)
        assertThat(ClosedSubstituteRequestResponse::class.java.recordComponents.map { it.name })
            .containsExactlyInAnyOrder(
                "requestId", "shiftId", "startAt", "endAt", "shiftStatus", "outcome", "closeReason", "closedAt",
                "requesterName", "requesterMembershipEnded", "accepterName",
                "requesterStillAssigned", "shiftStarted", "hasNewerActiveRequest",
            )
        assertThat(ClosedSubstituteRequestPageResponse::class.java.recordComponents.map { it.name })
            .containsExactly("items", "page", "size", "hasNext")
    }

    // V4-N-54
    @Test
    @DisplayName("CLOSED 종료 시각과 만료 근무 시작 시각을 섞어 DB에서 정렬하고, 같은 시각이면 요청 ID 내림차순이며 페이지를 넘어도 같다")
    fun mixedEffectiveTimeOrderInDatabase() {
        // given: ID 순서 B < C < D < A
        val place = newPlace()
        val b = createRequest(place, daysUntilShift = 1) // 수락 대기 → 만료 (효력 종료 = 근무 시작 T2)
        val c = createRequest(place, daysUntilShift = 7) // T2에 관리자 종료
        val d = createRequest(place, daysUntilShift = 7) // T2 + 1시간에 관리자 종료
        val a = createRequest(place, daysUntilShift = 7) // 지금(T0) 관리자 종료
        substituteRequestService.close(a.requestId, place.managerUserId)
        testClock.setInstant(b.startAt.atZone(testClock.zone).toInstant())
        substituteRequestService.close(c.requestId, place.managerUserId)
        moveClock(hours = 1)
        substituteRequestService.close(d.requestId, place.managerUserId)

        // when
        val all = closedOf(place).items
        val onePerPage = (0..3).map { closedOf(place, page = it, size = 1) }

        // then: D(T2+1h) → C(T2, ID 큼) → B(T2, 만료) → A(T0). ID 순서와 다름
        val expected = listOf(d.requestId, c.requestId, b.requestId, a.requestId)
        assertThat(all.map { it.requestId }).containsExactlyElementsOf(expected)
        assertThat(all.map { it.closedAt }).containsExactly(b.startAt.plusHours(1), b.startAt, b.startAt, FIXED_NOW_LOCAL)
        assertThat(onePerPage.flatMap { p -> p.items.map { it.requestId } }).containsExactlyElementsOf(expected)
        assertThat(onePerPage.map { it.hasNext }).containsExactly(true, true, true, false)
    }

    // ===================== 실행 헬퍼 =====================

    private fun closedOf(
        place: Place,
        actorUserId: Long = place.managerUserId,
        from: LocalDateTime? = null,
        to: LocalDateTime? = null,
        page: Int = 0,
        size: Int = 20,
    ): ClosedSubstituteRequestPageResponse =
        substituteRequestService.findClosedRequests(place.workplaceId, actorUserId, from, to, page, size)

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    private fun moveClock(days: Long = 0, hours: Long = 0) {
        testClock.setInstant(testClock.instant().plusSeconds(days * 86_400 + hours * 3_600))
    }

    private fun inTransaction(block: () -> Unit) {
        TransactionTemplate(transactionManager).executeWithoutResult { block() }
    }

    private fun changeShiftMember(shiftId: Long, memberId: Long) {
        inTransaction {
            shiftRepository.findByIdForUpdate(shiftId).orElseThrow()
                .changeMember(workplaceMemberRepository.findById(memberId).orElseThrow())
        }
    }

    // ===================== 검증 헬퍼 =====================

    // 같은 시각의 끝난 목록과 진행 중 목록(SUB-06)은 겹치지 않고 합치면 승인 안 된 요청 전부
    private fun assertSplit(place: Place, all: Set<Long>) {
        val closedIds = closedOf(place).items.map { it.requestId }
        val ongoingIds = substituteRequestService.list(place.workplaceId, place.managerUserId).map { it.requestId }
        assertThat(closedIds).doesNotContainAnyElementsOf(ongoingIds)
        assertThat(closedIds + ongoingIds).containsExactlyInAnyOrderElementsOf(all)
    }

    private fun storedStatus(requestId: Long): RequestStatus = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            substituteRequestRepository.findById(requestId).orElseThrow().status
        }
    )

    private fun storedClosedAt(requestId: Long): LocalDateTime? =
        TransactionTemplate(transactionManager).execute {
            substituteRequestRepository.findById(requestId).orElseThrow().closedAt
        }

    private fun assertError(expected: ErrorCode, action: () -> Unit) {
        assertThatThrownBy { action() }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(expected)
    }

    // ===================== Fixture =====================

    private data class Place(val workplaceId: Long, val managerUserId: Long)

    private data class MemberRef(val userId: Long, val memberId: Long)

    private data class RequestRef(
        val requestId: Long,
        val shiftId: Long,
        val startAt: LocalDateTime,
        val requesterUserId: Long,
        val requesterMemberId: Long,
        val requesterName: String,
        val accepterUserId: Long,
        val accepterMemberId: Long,
        val accepterCandidateId: Long,
        val accepterName: String,
        val otherCandidateUserId: Long,
        val otherCandidateMemberId: Long,
        val otherCandidateId: Long,
        val otherCandidateName: String,
    )

    // 근무지와 관리자 1명
    private fun newPlace(): Place = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = workplaceRepository.save(Workplace("끝난 대타 조회 테스트 매장 $suffix", "CLS-$suffix"))
            val manager = saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
            Place(requireNotNull(workplace.id), requireNotNull(manager.user.id))
        }
    )

    private fun addMember(place: Place, role: WorkplaceRole): MemberRef = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val member = saveMember(workplaceRepository.findById(place.workplaceId).orElseThrow(), "member-${uniqueSuffix()}", role)
            MemberRef(requireNotNull(member.user.id), requireNotNull(member.id))
        }
    )

    // 요청자·후보 2명, 요청자의 근무 1개에 요청 1건. accept면 첫 후보가 실제 수락 흐름으로 ACCEPTED
    private fun createRequest(place: Place, daysUntilShift: Long, accept: Boolean = true): RequestRef {
        val r = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val suffix = uniqueSuffix()
                val workplace = workplaceRepository.findById(place.workplaceId).orElseThrow()
                val startAt = now().plusDays(daysUntilShift).withHour(10).withMinute(0).withSecond(0).withNano(0)
                val schedule = savePublishedSchedule(workplace, startAt)

                val requester = saveMember(workplace, "requester-$suffix", WorkplaceRole.EMPLOYEE)
                val accepter = saveMember(workplace, "accepter-$suffix", WorkplaceRole.EMPLOYEE)
                val other = saveMember(workplace, "other-$suffix", WorkplaceRole.EMPLOYEE)

                val shift = shiftRepository.save(Shift(schedule, requester, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))
                val request = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))
                val accepterCandidate = substituteCandidateRepository.save(SubstituteCandidate(request, accepter))
                val otherCandidate = substituteCandidateRepository.save(SubstituteCandidate(request, other))

                RequestRef(
                    requestId = requireNotNull(request.id),
                    shiftId = requireNotNull(shift.id),
                    startAt = startAt,
                    requesterUserId = requireNotNull(requester.user.id),
                    requesterMemberId = requireNotNull(requester.id),
                    requesterName = requester.user.name,
                    accepterUserId = requireNotNull(accepter.user.id),
                    accepterMemberId = requireNotNull(accepter.id),
                    accepterCandidateId = requireNotNull(accepterCandidate.id),
                    accepterName = accepter.user.name,
                    otherCandidateUserId = requireNotNull(other.user.id),
                    otherCandidateMemberId = requireNotNull(other.id),
                    otherCandidateId = requireNotNull(otherCandidate.id),
                    otherCandidateName = other.user.name,
                )
            }
        )

        if (accept) {
            substituteCandidateService.acceptCandidate(r.accepterCandidateId, r.accepterUserId)
        }

        return r
    }

    // 같은 근무에 요청자가 새 OPEN 요청 1건 (후보 1명). 새 요청 ID를 돌려줌
    private fun addRequestOnShift(place: Place, base: RequestRef): Long = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val shift = shiftRepository.findById(base.shiftId).orElseThrow()
            val requester = workplaceMemberRepository.findById(base.requesterMemberId).orElseThrow()
            val candidate = saveMember(workplaceRepository.findById(place.workplaceId).orElseThrow(), "next-${uniqueSuffix()}", WorkplaceRole.EMPLOYEE)
            val request = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))
            substituteCandidateRepository.save(SubstituteCandidate(request, candidate))
            request.id
        }
    )

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
        private val FIXED_NOW_LOCAL: LocalDateTime = LocalDateTime.of(2026, 9, 15, 10, 0)

        private fun uniqueSuffix(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}
