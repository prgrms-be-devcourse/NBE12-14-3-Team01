package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.event.SubstituteAllCandidatesRejectedEvent
import com.merge.backend.domain.substitute.event.SubstituteCandidateAcceptedEvent
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.util.ReflectionTestUtils
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional

/*
 * Entity(Request/Candidate/Shift...)는 mock이 아닌 실제 객체를 사용한다.
 * → verify(mock)가 아니라 "실제 상태가 바뀌었는지 / 실패 시 그대로인지"를 검증하기 위함
 */
@ExtendWith(MockitoExtension::class)
class SubstituteCandidateServiceTest {

    @Mock
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Mock
    private lateinit var shiftRepository: ShiftRepository

    @Mock
    private lateinit var unavailableTimeRepository: UnavailableTimeRepository

    @Mock
    private lateinit var substituteCandidateRepository: SubstituteCandidateRepository

    @Mock
    private lateinit var substituteRequestRepository: SubstituteRequestRepository

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    // 2026-09-22 15:00 (KST)
    private val clock: Clock = Clock.fixed(
        Instant.parse("2026-09-22T06:00:00Z"),
        ZoneId.of("Asia/Seoul"),
    )

    private lateinit var substituteCandidateService: SubstituteCandidateService

    private lateinit var now: LocalDateTime
    private lateinit var workplace: Workplace
    private lateinit var schedule: Schedule
    private lateinit var requesterMember: WorkplaceMember
    private lateinit var candidateMember: WorkplaceMember
    private lateinit var shift: Shift
    private lateinit var request: SubstituteRequest
    private lateinit var candidate: SubstituteCandidate

    @BeforeEach
    fun setUp() {
        substituteCandidateService = SubstituteCandidateService(
            workplaceMemberRepository,
            shiftRepository,
            unavailableTimeRepository,
            substituteCandidateRepository,
            substituteRequestRepository,
            userService,
            clock,
            applicationEventPublisher,
        )

        now = LocalDateTime.now(clock)

        workplace = Workplace("SWITCH 카페", "1234").withId(WORKPLACE_ID)

        schedule = Schedule(workplace, now.toLocalDate()).apply {
            publish(now.minusDays(1))
        }

        requesterMember = member(REQUESTER_USER_ID, REQUESTER_MEMBER_ID, WorkplaceRole.EMPLOYEE)
        candidateMember = member(CANDIDATE_USER_ID, CANDIDATE_MEMBER_ID, WorkplaceRole.EMPLOYEE)

        // 내일 15:00 ~ 19:00 근무
        shift = Shift(
            schedule,
            requesterMember,
            now.plusDays(1),
            now.plusDays(1).plusHours(4),
            ShiftStatus.SCHEDULED,
        ).withId(SHIFT_ID)

        request = SubstituteRequest(shift, requesterMember, RequestStatus.OPEN).withId(REQUEST_ID)

        candidate = SubstituteCandidate(request, candidateMember).withId(CANDIDATE_ID)
    }

    // ===================== SUB-01 후보자 필터링 =====================

    @Nested
    @DisplayName("대타 후보자 조회 (findCandidates)")
    inner class FindCandidatesTest {

        @Test
        @DisplayName("요청자/MANAGER/공식 근무 충돌/근무 불가 시간/다른 대타 약속이 있는 멤버는 제외된다")
        fun findCandidates_FiltersIneligibleMembers() {

            // given
            val manager = member(31L, 131L, WorkplaceRole.MANAGER)
            val freeEmployee = member(32L, 132L, WorkplaceRole.EMPLOYEE)
            val shiftConflict = member(33L, 133L, WorkplaceRole.EMPLOYEE)
            val unavailable = member(34L, 134L, WorkplaceRole.EMPLOYEE)
            val activeSubstitute = member(35L, 135L, WorkplaceRole.EMPLOYEE)

            given(workplaceMemberRepository.findAllByWorkplace_IdAndLeftAtIsNull(WORKPLACE_ID))
                .willReturn(
                    listOf(
                        requesterMember, manager, freeEmployee,
                        shiftConflict, unavailable, activeSubstitute,
                    )
                )

            // 같은 메서드가 멤버마다 호출되므로 userId에 따라 결과를 돌려준다
            given(
                shiftRepository.existsOverlappingOfficialShift(
                    anyLong(), eq(ScheduleStatus.PUBLISHED),
                    eq(shift.startAt), eq(shift.endAt), eq(SHIFT_ID),
                )
            ).willAnswer { it.getArgument<Long>(0) == 33L }

            given(
                unavailableTimeRepository.existsOverlappingUnavailableTime(
                    anyLong(), eq(shift.startAt), eq(shift.endAt),
                )
            ).willAnswer { it.getArgument<Long>(0) == 34L }

            given(
                substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                    anyLong(), eq(shift.startAt), eq(shift.endAt), eq(now),
                )
            ).willAnswer { it.getArgument<Long>(0) == 35L }

            // when
            val result = substituteCandidateService.findCandidates(
                WORKPLACE_ID, REQUESTER_MEMBER_ID, shift,
            )

            // then
            assertThat(result).containsExactly(freeEmployee)
        }

        @Test
        @DisplayName("조건을 만족하는 멤버가 없으면 빈 리스트를 반환한다")
        fun findCandidates_NoEligibleMember_ReturnsEmpty() {

            // given - 요청자 본인만 남아 있는 근무지
            given(workplaceMemberRepository.findAllByWorkplace_IdAndLeftAtIsNull(WORKPLACE_ID))
                .willReturn(listOf(requesterMember))

            // when
            val result = substituteCandidateService.findCandidates(
                WORKPLACE_ID, REQUESTER_MEMBER_ID, shift,
            )

            // then
            assertThat(result).isEmpty()
        }
    }

    // ===================== SUB-02 / SUB-07 조회 =====================

    @Nested
    @DisplayName("받은 요청 / 수락 대기 요청 조회")
    inner class FindRequestsTest {

        @Test
        @DisplayName("SUB-02 - 현재 사용자 ID와 현재 시각으로 받은 요청을 조회한다")
        fun findReceivedRequests_Success() {

            // given
            given(substituteCandidateRepository.findReceivedRequests(CANDIDATE_USER_ID, now))
                .willReturn(listOf(candidate))

            // when
            val result = substituteCandidateService.findReceivedRequests(CANDIDATE_USER_ID)

            // then
            assertThat(result).containsExactly(candidate)
        }

        @Test
        @DisplayName("SUB-07 - 현재 사용자 ID와 현재 시각으로 승인 대기 요청을 조회한다")
        fun findAcceptedPendingRequests_Success() {

            // given
            given(substituteCandidateRepository.findAcceptedPendingRequests(CANDIDATE_USER_ID, now))
                .willReturn(listOf(candidate))

            // when
            val result = substituteCandidateService.findAcceptedPendingRequests(CANDIDATE_USER_ID)

            // then
            assertThat(result).containsExactly(candidate)
        }
    }

    // ===================== SUB-03 수락 =====================

    @Nested
    @DisplayName("대타 요청 수락 (acceptCandidate)")
    inner class AcceptTest {

        @Test
        @DisplayName("성공 - Candidate와 Request가 ACCEPTED로 바뀌고 MANAGER에게 이벤트가 발행된다")
        fun acceptCandidate_Success() {

            // given
            givenRespondableCandidate()

            val manager = member(40L, 140L, WorkplaceRole.MANAGER)
            given(
                workplaceMemberRepository.findAllByWorkplace_IdAndRoleAndLeftAtIsNull(
                    WORKPLACE_ID, WorkplaceRole.MANAGER,
                )
            ).willReturn(listOf(manager))

            // when
            val result = substituteCandidateService.acceptCandidate(CANDIDATE_ID, CANDIDATE_USER_ID)

            // then - 실제 상태 변경
            assertThat(result).isSameAs(candidate)
            assertThat(candidate.status).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(candidate.respondedAt).isEqualTo(now)
            assertThat(request.status).isEqualTo(RequestStatus.ACCEPTED)

            // then - Shift 담당자는 MANAGER 승인 전까지 바뀌지 않는다
            assertThat(shift.member).isSameAs(requesterMember)

            // then - Request Lock → User Lock 순서
            inOrder(substituteRequestRepository, userService) {
                verify(substituteRequestRepository).findByIdForUpdate(REQUEST_ID)
                verify(userService).getByIdForUpdate(CANDIDATE_USER_ID)
            }

            // then - 후보자 기준으로 시간 충돌을 검사했는지
            verify(shiftRepository).existsOverlappingOfficialShift(
                CANDIDATE_USER_ID, ScheduleStatus.PUBLISHED,
                shift.startAt, shift.endAt, SHIFT_ID,
            )
            verify(unavailableTimeRepository).existsOverlappingUnavailableTime(
                CANDIDATE_USER_ID, shift.startAt, shift.endAt,
            )
            verify(substituteCandidateRepository).existsOverlappingAcceptedSubstitute(
                CANDIDATE_USER_ID, shift.startAt, shift.endAt, now,
            )

            // then - 이벤트
            val eventCaptor = argumentCaptor<SubstituteCandidateAcceptedEvent>()
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture())

            assertThat(eventCaptor.lastValue.requestId).isEqualTo(REQUEST_ID)
            assertThat(eventCaptor.lastValue.managerMemberIds).containsExactly(140L)
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 Candidate")
        fun acceptCandidate_CandidateNotFound() {

            // given
            given(substituteCandidateRepository.findRequestIdById(CANDIDATE_ID))
                .willReturn(null)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.CANDIDATE_NOT_FOUND)
            verify(substituteRequestRepository, never()).findByIdForUpdate(anyLong())
        }

        @Test
        @DisplayName("실패 - Candidate가 가리키는 Request가 존재하지 않음")
        fun acceptCandidate_RequestNotFound() {

            // given
            given(substituteCandidateRepository.findRequestIdById(CANDIDATE_ID))
                .willReturn(REQUEST_ID)
            given(substituteRequestRepository.findByIdForUpdate(REQUEST_ID))
                .willReturn(null)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND)
        }

        @Test
        @DisplayName("실패 - 다른 사람의 Candidate를 수락할 수 없다")
        fun acceptCandidate_NotOwnCandidate() {

            // given
            givenRespondableCandidate()

            // when & then
            assertFailsWithoutSideEffect(SubstituteRequestErrorCode.NOT_OWN_CANDIDATE) {
                substituteCandidateService.acceptCandidate(CANDIDATE_ID, OTHER_USER_ID)
            }
        }

        @Test
        @DisplayName("실패 - 퇴사한 후보자는 수락할 수 없다")
        fun acceptCandidate_LeftMember() {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(candidateMember, "leftAt", now.minusHours(1))

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER)
        }

        @Test
        @DisplayName("실패 - 후보자가 더 이상 EMPLOYEE가 아니면 수락할 수 없다")
        fun acceptCandidate_NotEmployee() {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(candidateMember, "role", WorkplaceRole.MANAGER)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER)
        }

        @ParameterizedTest(name = "Candidate 상태 = {0}")
        @EnumSource(value = CandidateStatus::class, names = ["ACCEPTED", "REJECTED"])
        @DisplayName("실패 - 이미 응답한 Candidate는 다시 수락할 수 없다")
        fun acceptCandidate_AlreadyResponded(status: CandidateStatus) {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(candidate, "status", status)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.CANDIDATE_ALREADY_RESPONDED)
        }

        @ParameterizedTest(name = "Request 상태 = {0}")
        @EnumSource(value = RequestStatus::class, names = ["ACCEPTED", "APPROVED", "CLOSED"])
        @DisplayName("실패 - Request가 OPEN이 아니면 수락할 수 없다")
        fun acceptCandidate_RequestNotOpen(status: RequestStatus) {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(request, "status", status)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.REQUEST_NOT_OPEN)
        }

        @Test
        @DisplayName("실패 - 공개되지 않은 근무표의 Shift")
        fun acceptCandidate_ScheduleNotPublished() {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(schedule, "status", ScheduleStatus.DRAFT)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.SHIFT_NOT_PUBLISHED)
        }

        @Test
        @DisplayName("실패 - 취소된 Shift")
        fun acceptCandidate_ShiftCancelled() {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(shift, "status", ShiftStatus.CANCELLED)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.SHIFT_CANCELLED)
        }

        @Test
        @DisplayName("실패 - 이미 시작된 Shift (startAt < now)")
        fun acceptCandidate_ShiftAlreadyStarted() {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(shift, "startAt", now.minusMinutes(1))

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED)
        }

        @Test
        @DisplayName("실패 - Shift 시작 시각이 정확히 now (경계값)")
        fun acceptCandidate_ShiftStartsExactlyNow() {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(shift, "startAt", now)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED)
        }

        @Test
        @DisplayName("실패 - 다른 공식 근무와 시간이 겹침")
        fun acceptCandidate_OfficialShiftConflict() {

            // given
            givenRespondableCandidate()
            given(
                shiftRepository.existsOverlappingOfficialShift(
                    CANDIDATE_USER_ID, ScheduleStatus.PUBLISHED,
                    shift.startAt, shift.endAt, SHIFT_ID,
                )
            ).willReturn(true)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.CONFLICT_SHIFT)
        }

        @Test
        @DisplayName("실패 - 근무 불가 시간과 겹침")
        fun acceptCandidate_UnavailableTimeConflict() {

            // given
            givenRespondableCandidate()
            given(
                unavailableTimeRepository.existsOverlappingUnavailableTime(
                    CANDIDATE_USER_ID, shift.startAt, shift.endAt,
                )
            ).willReturn(true)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME)
        }

        @Test
        @DisplayName("실패 - 이미 수락한 다른 대타 약속과 겹침")
        fun acceptCandidate_ActiveSubstituteConflict() {

            // given
            givenRespondableCandidate()
            given(
                substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                    CANDIDATE_USER_ID, shift.startAt, shift.endAt, now,
                )
            ).willReturn(true)

            // when & then
            assertAcceptFails(SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE)
        }

        private fun assertAcceptFails(expected: ErrorCode) {
            assertFailsWithoutSideEffect(expected) {
                substituteCandidateService.acceptCandidate(CANDIDATE_ID, CANDIDATE_USER_ID)
            }
        }
    }

    // ===================== SUB-03 거절 =====================

    @Nested
    @DisplayName("대타 요청 거절 (rejectCandidate)")
    inner class RejectTest {

        @Test
        @DisplayName("성공 - 다른 PENDING 후보가 남아 있으면 Request는 OPEN을 유지한다")
        fun rejectCandidate_OtherPendingCandidateExists() {

            // given
            givenRespondableCandidate()
            given(
                substituteCandidateRepository.existsRespondableCandidateExcept(
                    REQUEST_ID, CANDIDATE_MEMBER_ID,
                )
            ).willReturn(true)

            // when
            substituteCandidateService.rejectCandidate(CANDIDATE_ID, CANDIDATE_USER_ID)

            // then
            assertThat(candidate.status).isEqualTo(CandidateStatus.REJECTED)
            assertThat(candidate.respondedAt).isEqualTo(now)

            assertThat(request.status).isEqualTo(RequestStatus.OPEN)
            assertThat(request.closeReason).isNull()
            assertThat(request.closedAt).isNull()

            verify(applicationEventPublisher, never()).publishEvent(anyOrNull<Any>())
        }

        @Test
        @DisplayName("성공 - 마지막 후보가 거절하면 Request가 CLOSED(ALL_CANDIDATES_REJECTED) 된다")
        fun rejectCandidate_LastCandidate_ClosesRequest() {

            // given
            givenRespondableCandidate()
            given(
                substituteCandidateRepository.existsRespondableCandidateExcept(
                    REQUEST_ID, CANDIDATE_MEMBER_ID,
                )
            ).willReturn(false)

            // 요청자가 동시에 MANAGER 목록에 포함된 경우 → 알림 수신자 중복 제거 확인
            val otherManager = member(41L, 141L, WorkplaceRole.MANAGER)
            given(
                workplaceMemberRepository.findAllByWorkplace_IdAndRoleAndLeftAtIsNull(
                    WORKPLACE_ID, WorkplaceRole.MANAGER,
                )
            ).willReturn(listOf(requesterMember, otherManager))

            // when
            substituteCandidateService.rejectCandidate(CANDIDATE_ID, CANDIDATE_USER_ID)

            // then
            assertThat(candidate.status).isEqualTo(CandidateStatus.REJECTED)

            assertThat(request.status).isEqualTo(RequestStatus.CLOSED)
            assertThat(request.closeReason).isEqualTo(RequestCloseReason.ALL_CANDIDATES_REJECTED)
            assertThat(request.closedAt).isEqualTo(now)

            val eventCaptor = argumentCaptor<SubstituteAllCandidatesRejectedEvent>()
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture())

            assertThat(eventCaptor.lastValue.requestId).isEqualTo(REQUEST_ID)
            assertThat(eventCaptor.lastValue.recipientMemberIds)
                .containsExactlyInAnyOrder(REQUESTER_MEMBER_ID, 141L)
        }

        @Test
        @DisplayName("실패 - 다른 사람의 Candidate를 거절할 수 없다")
        fun rejectCandidate_NotOwnCandidate() {

            // given
            givenRespondableCandidate()

            // when & then
            assertFailsWithoutSideEffect(SubstituteRequestErrorCode.NOT_OWN_CANDIDATE) {
                substituteCandidateService.rejectCandidate(CANDIDATE_ID, OTHER_USER_ID)
            }
        }

        @Test
        @DisplayName("실패 - 퇴사한 후보자는 거절할 수 없다")
        fun rejectCandidate_LeftMember() {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(candidateMember, "leftAt", now.minusHours(1))

            // when & then
            assertRejectFails(SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER)
        }

        @Test
        @DisplayName("실패 - 이미 응답한 Candidate는 다시 거절할 수 없다")
        fun rejectCandidate_AlreadyResponded() {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(candidate, "status", CandidateStatus.REJECTED)

            // when & then
            assertRejectFails(SubstituteRequestErrorCode.CANDIDATE_ALREADY_RESPONDED)
        }

        @Test
        @DisplayName("실패 - 다른 후보가 이미 수락한 Request는 거절할 수 없다")
        fun rejectCandidate_RequestNotOpen() {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(request, "status", RequestStatus.ACCEPTED)

            // when & then
            assertRejectFails(SubstituteRequestErrorCode.REQUEST_NOT_OPEN)
        }

        @Test
        @DisplayName("실패 - 이미 시작된 Shift는 거절할 수 없다")
        fun rejectCandidate_ShiftAlreadyStarted() {

            // given
            givenRespondableCandidate()
            ReflectionTestUtils.setField(shift, "startAt", now)

            // when & then
            assertRejectFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED)
        }

        private fun assertRejectFails(expected: ErrorCode) {
            assertFailsWithoutSideEffect(expected) {
                substituteCandidateService.rejectCandidate(CANDIDATE_ID, CANDIDATE_USER_ID)
            }

            // 실패했다면 "남은 후보 확인 → Request 종료" 단계까지 가면 안 된다
            verify(substituteCandidateRepository, never())
                .existsRespondableCandidateExcept(anyOrNull(), anyOrNull())
        }
    }

    // ===================== 공용 헬퍼 =====================

    // Request Lock → Candidate 조회까지 정상 동작하도록 설정
    private fun givenRespondableCandidate() {
        given(substituteCandidateRepository.findRequestIdById(CANDIDATE_ID))
            .willReturn(REQUEST_ID)
        given(substituteRequestRepository.findByIdForUpdate(REQUEST_ID))
            .willReturn(request)
        given(substituteCandidateRepository.findById(CANDIDATE_ID))
            .willReturn(Optional.of(candidate))
    }

    // 지정한 에러코드로 실패하고, 상태 변경 / 이벤트 발행이 일어나지 않았는지 확인
    private fun assertFailsWithoutSideEffect(expected: ErrorCode, action: () -> Unit) {
        val candidateStatusBefore = candidate.status
        val requestStatusBefore = request.status

        assertThatThrownBy { action() }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(expected)

        assertThat(candidate.status).isEqualTo(candidateStatusBefore)
        assertThat(candidate.respondedAt).isNull()
        assertThat(request.status).isEqualTo(requestStatusBefore)
        assertThat(request.closedAt).isNull()
        assertThat(shift.member).isSameAs(requesterMember)

        verify(applicationEventPublisher, never()).publishEvent(anyOrNull<Any>())
    }

    private fun member(userId: Long, memberId: Long, role: WorkplaceRole): WorkplaceMember {
        val user = User(userId, "user$userId@test.com", "user$userId")
        return WorkplaceMember(workplace, user, role, now.minusDays(30)).withId(memberId)
    }

    companion object {
        private const val WORKPLACE_ID = 100L
        private const val SHIFT_ID = 300L
        private const val REQUEST_ID = 50L
        private const val CANDIDATE_ID = 1L

        private const val REQUESTER_USER_ID = 10L
        private const val CANDIDATE_USER_ID = 20L
        private const val OTHER_USER_ID = 99L

        private const val REQUESTER_MEMBER_ID = 15L
        private const val CANDIDATE_MEMBER_ID = 25L
    }
}

private fun <T : Any> T.withId(id: Long): T = apply {
    ReflectionTestUtils.setField(this, "id", id)
}
