package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.entity.UnavailableTime
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.substitute.dto.SubstituteRequestListResponse
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
import com.merge.backend.global.exception.ErrorCode
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowingConsumer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/*
 * mock 없이 실제 DB(MySQL Testcontainers)로 대타 요청 전체 흐름을 검증한다.
 * 각 단계 후 flush/clear 하고 DB에서 다시 조회해 "실제로 저장된 값"을 확인한다.
 * 테스트 종료 시 롤백된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
@Transactional
class SubstituteFlowIntegrationTest {

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
    private lateinit var unavailableTimeRepository: UnavailableTimeRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var testClock: TestClock

    private lateinit var now: LocalDateTime
    private lateinit var workplace: Workplace
    private lateinit var schedule: Schedule
    private lateinit var manager: WorkplaceMember
    private lateinit var requester: WorkplaceMember
    private lateinit var candidateA: WorkplaceMember
    private lateinit var candidateB: WorkplaceMember
    private lateinit var shift: Shift

    @BeforeEach
    fun setUp() {
        testClock.setInstant(FIXED_NOW)
        now = LocalDateTime.now(testClock)

        workplace = workplaceRepository.save(Workplace("대타 흐름 테스트 매장", "FLOW0001"))

        schedule = Schedule(
            workplace,
            now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        )
        schedule.publish(now.minusDays(1))
        schedule = scheduleRepository.save(schedule)

        manager = saveMember("manager", WorkplaceRole.MANAGER)
        requester = saveMember("requester", WorkplaceRole.EMPLOYEE)
        candidateA = saveMember("candidate-a", WorkplaceRole.EMPLOYEE)
        candidateB = saveMember("candidate-b", WorkplaceRole.EMPLOYEE)

        // 이틀 뒤 10:00 ~ 14:00
        shift = saveShift(requester, now.plusDays(2).withHour(10), 4)

        flushAndClear()
    }

    // ===================== 전체 흐름 =====================

    @Nested
    @DisplayName("전체 흐름")
    inner class FullFlowTest {

        @Test
        @DisplayName("요청 → 수락 → 승인 → Shift 담당자가 수락자로 변경된다")
        fun requestAcceptApprove_ChangesShiftMember() {

            // 1. 요청 생성 - MANAGER와 요청자를 제외한 2명이 후보가 된다
            val created = substituteRequestService.create(shift.id, requester.userId)

            assertThat(created.requestCreated).isTrue()
            assertThat(created.status).isEqualTo(RequestStatus.OPEN)
            assertThat(created.candidateCount).isEqualTo(2)

            val requestId = created.requestId
            flushAndClear()

            assertThat(candidatesOf(requestId))
                .extracting<Long> { it.member.id }
                .containsExactlyInAnyOrder(candidateA.id, candidateB.id)

            // 2. 후보자 A가 받은 요청 목록에서 확인
            assertThat(substituteCandidateService.findReceivedRequests(candidateA.userId))
                .extracting<Long> { it.request.id }
                .containsExactly(requestId)

            // 3. 후보자 A 수락
            substituteCandidateService.acceptCandidate(candidateIdOf(requestId, candidateA), candidateA.userId)
            flushAndClear()

            assertThat(findRequest(requestId).status).isEqualTo(RequestStatus.ACCEPTED)
            assertThat(findCandidate(requestId, candidateA).status).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(findCandidate(requestId, candidateA).respondedAt).isEqualTo(now)

            // 아직 승인 전이므로 Shift 담당자는 그대로
            assertThat(findShift().member.id).isEqualTo(requester.id)

            // 수락 이후: A는 승인 대기 목록에, B의 받은 요청 목록에서는 사라진다
            assertThat(substituteCandidateService.findAcceptedPendingRequests(candidateA.userId))
                .hasSize(1)
            assertThat(substituteCandidateService.findReceivedRequests(candidateB.userId))
                .isEmpty()

            // 4. MANAGER 목록 조회 - 수락자 정보가 함께 내려온다
            val list = substituteRequestService.list(workplace.id, manager.userId)

            assertThat(list).singleElement().satisfies(
                ThrowingConsumer<SubstituteRequestListResponse> { response ->
                    assertThat(response.requestId).isEqualTo(requestId)
                    assertThat(response.status).isEqualTo(RequestStatus.ACCEPTED)
                    assertThat(response.acceptedMember?.memberId).isEqualTo(candidateA.id)
                }
            )

            // 5. MANAGER 승인
            substituteRequestService.approve(requestId, manager.userId)
            flushAndClear()

            // 6. DB에서 다시 조회해 실제 변경 확인
            val approved = findRequest(requestId)
            assertThat(approved.status).isEqualTo(RequestStatus.APPROVED)
            assertThat(approved.approvedAt).isEqualTo(now)
            assertThat(approved.closedAt).isEqualTo(now)

            assertThat(findShift().member.id).isEqualTo(candidateA.id)

            assertThat(findCandidate(requestId, candidateB).status).isEqualTo(CandidateStatus.PENDING)

            // 요청자의 보낸 요청 목록에는 APPROVED로 보인다
            assertThat(substituteRequestService.getSentRequests(requester.userId))
                .extracting<RequestStatus> { it.status }
                .containsExactly(RequestStatus.APPROVED)

            // 이제 이 Shift는 A의 근무이므로 원래 요청자는 다시 요청할 수 없다
            assertBusinessError(SubstituteRequestErrorCode.NOT_OWN_SHIFT) {
                substituteRequestService.create(shift.id, requester.userId)
            }
        }

        @Test
        @DisplayName("후보 전원이 거절하면 CLOSED 되고, 같은 Shift로 다시 요청할 수 있다")
        fun allCandidatesReject_ClosesRequest_ThenCanRequestAgain() {

            // given
            val requestId = createRequest()

            // when
            substituteCandidateService.rejectCandidate(candidateIdOf(requestId, candidateA), candidateA.userId)
            flushAndClear()

            // then - 한 명 남았으므로 아직 OPEN
            assertThat(findRequest(requestId).status).isEqualTo(RequestStatus.OPEN)

            // when - 마지막 후보도 거절
            substituteCandidateService.rejectCandidate(candidateIdOf(requestId, candidateB), candidateB.userId)
            flushAndClear()

            // then
            val closed = findRequest(requestId)
            assertThat(closed.status).isEqualTo(RequestStatus.CLOSED)
            assertThat(closed.closeReason).isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED)
            assertThat(closed.closedAt).isEqualTo(now)

            assertThat(findShift().member.id).isEqualTo(requester.id)

            // 활성 요청이 없으므로 다시 요청 가능
            val retried = substituteRequestService.create(shift.id, requester.userId)

            assertThat(retried.requestCreated).isTrue()
            assertThat(retried.requestId).isNotEqualTo(requestId)
        }

        @Test
        @DisplayName("수락 후 MANAGER가 종료하면 Shift 담당자는 그대로이고, 이후 승인할 수 없다")
        fun acceptThenManagerClose_ShiftUnchanged_CannotApprove() {

            // given
            val requestId = createRequest()
            substituteCandidateService.acceptCandidate(candidateIdOf(requestId, candidateA), candidateA.userId)
            flushAndClear()

            // when
            substituteRequestService.close(requestId, manager.userId)
            flushAndClear()

            // then
            val closed = findRequest(requestId)
            assertThat(closed.status).isEqualTo(RequestStatus.CLOSED)
            assertThat(closed.closeReason).isEqualTo(RequestCloseReason.MANAGER_CLOSED)
            assertThat(findShift().member.id).isEqualTo(requester.id)

            assertBusinessError(SubstituteRequestErrorCode.INVALID_REQUEST) {
                substituteRequestService.approve(requestId, manager.userId)
            }
        }
    }

    // ===================== 수락 / 거절 검증 =====================

    @Nested
    @DisplayName("수락 / 거절 실패 시 DB가 바뀌지 않는다")
    inner class RespondValidationTest {

        @Test
        @DisplayName("요청 이후 등록한 근무 불가 시간과 겹치면 수락할 수 없다")
        fun cannotAcceptWhenUnavailableTimeOverlaps() {

            // given
            val requestId = createRequest()
            unavailableTimeRepository.save(
                UnavailableTime(
                    userRepository.findById(requireNotNull(candidateA.userId)).orElseThrow(),
                    shift.startAt.plusHours(1),
                    shift.endAt.plusHours(1),
                )
            )
            flushAndClear()

            // when & then
            assertBusinessError(SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME) {
                substituteCandidateService.acceptCandidate(candidateIdOf(requestId, candidateA), candidateA.userId)
            }

            assertUntouched(requestId)
        }

        @Test
        @DisplayName("이미 수락한 다른 대타 약속과 시간이 겹치면 수락할 수 없다")
        fun cannotAcceptWhenOtherSubstituteOverlaps() {

            // given - 같은 시간대에 다른 직원의 근무와 대타 요청이 하나 더 있다
            val otherRequester = saveMember("other-requester", WorkplaceRole.EMPLOYEE)
            val otherShift = saveShift(otherRequester, shift.startAt.plusHours(2), 4)
            flushAndClear()

            val requestId = createRequest()
            val otherRequestId = substituteRequestService.create(otherShift.id, otherRequester.userId).requestId
            flushAndClear()

            // A가 다른 요청을 먼저 수락
            substituteCandidateService.acceptCandidate(candidateIdOf(otherRequestId, candidateA), candidateA.userId)
            flushAndClear()

            // when & then
            assertBusinessError(SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE) {
                substituteCandidateService.acceptCandidate(candidateIdOf(requestId, candidateA), candidateA.userId)
            }

            assertUntouched(requestId)
        }
    }

    // ===================== 승인 검증 =====================

    @Nested
    @DisplayName("승인 실패 시 Shift 담당자가 바뀌지 않는다")
    inner class ApproveValidationTest {

        @Test
        @DisplayName("수락한 사람이 퇴사하면 승인으로 이어지지 않는다")
        fun cannotApproveWhenAcceptedMemberLeft() {

            // given
            val requestId = acceptedRequestByA()
            leave(candidateA)

            // when & then
            assertBusinessError(ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE) {
                substituteRequestService.approve(requestId, manager.userId)
            }

            assertNotApproved(requestId)
        }

        private fun acceptedRequestByA(): Long? {
            val requestId = createRequest()
            substituteCandidateService.acceptCandidate(candidateIdOf(requestId, candidateA), candidateA.userId)
            flushAndClear()
            return requestId
        }

        private fun assertNotApproved(requestId: Long?) {
            flushAndClear()
            assertThat(findRequest(requestId).status).isEqualTo(RequestStatus.ACCEPTED)
            assertThat(findRequest(requestId).approvedAt).isNull()
            assertThat(findShift().member.id).isEqualTo(requester.id)
        }
    }

    // ===================== 헬퍼 =====================

    private fun createRequest(): Long? {
        val requestId = substituteRequestService.create(shift.id, requester.userId).requestId
        flushAndClear()
        return requestId
    }

    // 실패한 요청이 Request / Candidate / Shift 어느 것도 바꾸지 않았는지 DB 기준으로 확인
    private fun assertUntouched(requestId: Long?) {
        flushAndClear()

        val request = findRequest(requestId)
        assertThat(request.status).isEqualTo(RequestStatus.OPEN)
        assertThat(request.closedAt).isNull()

        assertThat(candidatesOf(requestId)).allSatisfy(
            ThrowingConsumer<SubstituteCandidate> { candidate ->
                assertThat(candidate.status).isEqualTo(CandidateStatus.PENDING)
                assertThat(candidate.respondedAt).isNull()
            }
        )

        assertThat(findShift().member.id).isEqualTo(requester.id)
    }

    private fun assertBusinessError(expected: ErrorCode, action: () -> Unit) {
        assertThatThrownBy { action() }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(expected)
    }

    // 퇴사 API가 없으므로 leftAt을 직접 채운다
    private fun leave(member: WorkplaceMember) {
        // 컨텍스트에 Lazy 프록시가 남아 있으면 findById가 프록시를 돌려주고,
        // 프록시에 setField 하면 실제 엔티티가 바뀌지 않으므로 먼저 비운다
        flushAndClear()

        val managed = workplaceMemberRepository.findById(member.id).orElseThrow()
        ReflectionTestUtils.setField(managed, "leftAt", now.minusHours(1))
        flushAndClear()
    }

    private fun candidatesOf(requestId: Long?): List<SubstituteCandidate> =
        substituteCandidateRepository.findAll().filter { it.request.id == requestId }

    private fun findCandidate(requestId: Long?, member: WorkplaceMember): SubstituteCandidate =
        candidatesOf(requestId).first { it.member.id == member.id }

    private fun candidateIdOf(requestId: Long?, member: WorkplaceMember): Long? =
        findCandidate(requestId, member).id

    private fun findRequest(requestId: Long?): SubstituteRequest =
        substituteRequestRepository.findById(requireNotNull(requestId)).orElseThrow()

    private fun findShift(): Shift = shiftRepository.findById(shift.id).orElseThrow()

    private val WorkplaceMember.userId: Long?
        get() = user.id

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun saveMember(name: String, role: WorkplaceRole): WorkplaceMember {
        val user = userRepository.save(User("flow-$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, now.minusDays(30)))
    }

    private fun saveShift(member: WorkplaceMember, startAt: LocalDateTime, hours: Int): Shift {
        val start = startAt.withMinute(0).withSecond(0).withNano(0)

        return shiftRepository.save(Shift(schedule, member, start, start.plusHours(hours.toLong()), ShiftStatus.SCHEDULED))
    }

    companion object {
        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")
    }
}
