package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.exception.ScheduleErrorCode
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.event.SubstituteNoCandidateEvent
import com.merge.backend.domain.substitute.event.SubstituteRequestApprovedEvent
import com.merge.backend.domain.substitute.event.SubstituteRequestCreatedEvent
import com.merge.backend.domain.substitute.event.SubstituteRequestManagerClosedEvent
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.domain.workplace.service.WorkplaceMemberService
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.util.ReflectionTestUtils
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SubstituteRequestServiceTest {

    private lateinit var substituteRequestService: SubstituteRequestService

    @Mock
    private lateinit var substituteRequestRepository: SubstituteRequestRepository

    @Mock
    private lateinit var substituteCandidateRepository: SubstituteCandidateRepository

    @Mock
    private lateinit var shiftRepository: ShiftRepository

    @Mock
    private lateinit var substituteCandidateService: SubstituteCandidateService

    @Mock
    private lateinit var workplaceRepository: WorkplaceRepository

    @Mock
    private lateinit var unavailableTimeRepository: UnavailableTimeRepository

    @Mock
    private lateinit var workplaceMemberService: WorkplaceMemberService

    @Mock
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    private val clock: Clock = Clock.fixed(
        Instant.parse("2026-09-22T12:00:00Z"),
        ZoneId.of("Asia/Seoul"),
    )

    private val requestId = 1L
    private val actorId = 10L
    private val workplaceId = 100L
    private val requesterMemberId = 15L

    private val candidateUserId = 200L
    private val now: LocalDateTime = LocalDateTime.now(clock)

    // mock은 MockitoExtension 세션 안(@BeforeEach)에서 만들어야 Strict Stubs 검사 대상이 된다
    private lateinit var member: WorkplaceMember
    private lateinit var workplace: Workplace
    private lateinit var requesterMember: WorkplaceMember
    private lateinit var candidateMember: WorkplaceMember
    private lateinit var candidateUser: User
    private lateinit var schedule: Schedule
    private lateinit var shift: Shift
    private lateinit var request: SubstituteRequest
    private lateinit var candidate: SubstituteCandidate

    @BeforeEach
    fun setUp() {
        substituteRequestService = SubstituteRequestService(
            substituteRequestRepository,
            substituteCandidateService,
            substituteCandidateRepository,
            shiftRepository,
            workplaceRepository,
            unavailableTimeRepository,
            userService,
            workplaceMemberService,
            workplaceMemberRepository,
            clock,
            applicationEventPublisher,
        )

        workplace = mock()
        member = mock()
        requesterMember = mock()
        candidateMember = mock()
        candidateUser = mock()
        schedule = mock()
        shift = mock()
        request = mock()
        candidate = mock()
    }

    private fun setupValidRequestBasicInfo() {
        given(workplace.id).willReturn(workplaceId)
        given(schedule.workplace).willReturn(workplace)
        given(shift.schedule).willReturn(schedule)
        given(request.shift).willReturn(shift)
        given(request.status).willReturn(RequestStatus.ACCEPTED)
    }

    private fun setupValidRequestFullInfo() {
        setupValidRequestBasicInfo()
        given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
        given(shift.status).willReturn(ShiftStatus.SCHEDULED)
        given(requesterMember.id).willReturn(requesterMemberId)
        given(shift.member).willReturn(requesterMember)
        given(request.requesterMember).willReturn(requesterMember)
    }

    private fun setupValidCandidateInfo() {
        given(candidateMember.workplace).willReturn(workplace)
        given(candidateMember.leftAt).willReturn(null)
        given(candidateMember.role).willReturn(WorkplaceRole.EMPLOYEE)
        given(candidateUser.id).willReturn(candidateUserId)
        given(candidateMember.user).willReturn(candidateUser)
        given(candidate.member).willReturn(candidateMember)
    }

    // 승인 직전 단계(Request/Workplace/ACCEPTED Candidate 조회)까지 정상 설정
    private fun setupApprovableRequest() {
        setupValidRequestFullInfo()
        given(substituteRequestRepository.findByIdForUpdate(requestId))
            .willReturn(Optional.of(request))
        given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace))
        given(substituteCandidateRepository.findByRequestIdAndStatus(requestId, CandidateStatus.ACCEPTED))
            .willReturn(Optional.of(candidate))
    }

    // 예외 타입뿐 아니라 "어떤 에러코드"로 실패했는지까지 확인
    private fun assertBusinessError(expected: ErrorCode, action: () -> Unit) {
        assertThatThrownBy { action() }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(expected)
    }

    private fun verifyNoApplicationEventPublished() {
        verify(applicationEventPublisher, never()).publishEvent(anyOrNull<Any>())
    }

    // 실패한 경우 승인(Shift 담당자 변경)과 이벤트 발행이 일어나지 않아야 한다
    private fun assertApproveFails(expected: ErrorCode) {
        assertBusinessError(expected) { substituteRequestService.approve(requestId, actorId) }

        verify(request, never()).approveRequest(anyOrNull(), anyOrNull())
        verifyNoApplicationEventPublished()
    }

    // 실패한 경우 종료 처리와 이벤트 발행이 일어나지 않아야 한다
    private fun assertCloseFails(expected: ErrorCode) {
        assertBusinessError(expected) { substituteRequestService.close(requestId, actorId) }

        verify(request, never()).closeByManager(anyOrNull())
        verifyNoApplicationEventPublished()
    }

    // ===================== SUB-04 대체 근무 승인 =====================

    @Nested
    @DisplayName("대체 근무 승인 (approve)")
    inner class ApproveTest {

        @Test
        @DisplayName("대체 근무 승인 성공")
        fun approve_success() {

            // given
            setupValidRequestFullInfo()
            setupValidCandidateInfo()

            val acceptedMemberId = 25L

            given(request.id).willReturn(requestId)
            given(candidateMember.id).willReturn(acceptedMemberId)
            given(shift.startAt).willReturn(now.plusDays(1))
            given(shift.endAt).willReturn(now.plusDays(1).plusHours(8))

            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))

            given(workplaceRepository.findById(workplaceId))
                .willReturn(Optional.of(workplace))

            given(substituteCandidateRepository.findByRequestIdAndStatus(requestId, CandidateStatus.ACCEPTED))
                .willReturn(Optional.of(candidate))

            given(shiftRepository.existsConflictingShift(eq(candidateUserId), anyOrNull(), anyOrNull()))
                .willReturn(false)

            given(
                unavailableTimeRepository.existsOverlappingUnavailableTime(
                    eq(candidateUserId), anyOrNull(), anyOrNull(),
                )
            ).willReturn(false)

            given(
                substituteCandidateRepository.existsConflictingActiveSubstitute(
                    eq(candidateUserId), eq(requestId), anyOrNull(), anyOrNull(), anyOrNull(),
                )
            ).willReturn(false)

            // when
            val result = substituteRequestService.approve(requestId, actorId)

            // then
            assertThat(result).isNotNull()

            inOrder(substituteRequestRepository, userService) {
                verify(substituteRequestRepository).findByIdForUpdate(requestId)
                verify(userService).getByIdForUpdate(candidateUserId)
            }

            verify(workplaceMemberService).requireManager(actorId, workplaceId)

            verify(request).approveRequest(eq(candidateMember), any<LocalDateTime>())

            // 승인 Event 검증
            val eventCaptor = argumentCaptor<SubstituteRequestApprovedEvent>()
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture())

            val event = eventCaptor.lastValue

            assertThat(event.requestId).isEqualTo(requestId)
            assertThat(event.requesterMemberId).isEqualTo(requesterMemberId)
            assertThat(event.acceptedMemberId).isEqualTo(acceptedMemberId)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 요청이면 예외가 발생한다")
        fun approve_fail_request_not_found() {
            // given
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.empty())

            // when & then
            assertBusinessError(SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND) {
                substituteRequestService.approve(requestId, actorId)
            }
            verifyNoApplicationEventPublished()
        }

        @ParameterizedTest(name = "Request 상태 = {0}")
        @EnumSource(value = RequestStatus::class, names = ["OPEN", "APPROVED", "CLOSED"])
        @DisplayName("실패: 아무도 수락하지 않았거나(OPEN) 이미 끝난 요청은 승인할 수 없다")
        fun approve_fail_invalid_request_status(status: RequestStatus) {
            // given
            given(request.status).willReturn(status)
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.INVALID_REQUEST)

            // 상태 검증에서 막혔으므로 다음 단계(권한 확인, User Lock)로 넘어가지 않는다
            verify(workplaceMemberService, never()).requireManager(anyOrNull(), anyOrNull())
            verify(userService, never()).getByIdForUpdate(anyOrNull())
        }

        @Test
        @DisplayName("실패: 승인 요청자가 해당 근무지 소속이 아니면 예외가 발생한다")
        fun approve_fail_actor_not_in_workplace() {
            // given
            setupValidRequestBasicInfo()
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willThrow(BusinessException(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER))

            // when & then
            assertApproveFails(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER)
            verify(substituteCandidateRepository, never()).findByRequestIdAndStatus(anyOrNull(), anyOrNull())
        }

        @Test
        @DisplayName("실패: 승인 요청자가 MANAGER가 아니면 예외가 발생한다")
        fun approve_fail_actor_not_manager() {
            // given
            setupValidRequestBasicInfo()
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willThrow(BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED))

            // when & then
            assertApproveFails(WorkplaceErrorCode.MANAGER_REQUIRED)
            verify(substituteCandidateRepository, never()).findByRequestIdAndStatus(anyOrNull(), anyOrNull())
        }

        @Test
        @DisplayName("실패: 근무표가 공개 전(DRAFT)이면 승인할 수 없다")
        fun approve_fail_schedule_not_published() {
            // given
            setupValidRequestBasicInfo()
            given(schedule.status).willReturn(ScheduleStatus.DRAFT)
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))

            // when & then
            assertApproveFails(ScheduleErrorCode.NOT_PUBLISHED)
        }

        @Test
        @DisplayName("실패: Shift가 취소됐으면 승인할 수 없다")
        fun approve_fail_shift_cancelled() {
            // given
            setupValidRequestBasicInfo()
            given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
            given(shift.status).willReturn(ShiftStatus.CANCELLED)
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))

            // when & then
            assertApproveFails(ShiftErrorCode.IS_CANCELED_SHIFT)
        }

        @Test
        @DisplayName("실패: 요청 이후 Shift 담당자가 바뀌었으면 승인할 수 없다")
        fun approve_fail_shift_member_changed() {
            // given
            setupValidRequestBasicInfo()
            given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
            given(shift.status).willReturn(ShiftStatus.SCHEDULED)

            val currentShiftMember = mock<WorkplaceMember>()
            given(currentShiftMember.id).willReturn(999L)
            given(shift.member).willReturn(currentShiftMember)

            given(requesterMember.id).willReturn(requesterMemberId)
            given(request.requesterMember).willReturn(requesterMember)

            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.INVALID_MEMBER)
        }

        @Test
        @DisplayName("실패: Request는 ACCEPTED인데 ACCEPTED Candidate가 없으면 예외가 발생한다")
        fun approve_fail_accepted_candidate_not_found() {
            // given
            setupValidRequestFullInfo()
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace))
            given(substituteCandidateRepository.findByRequestIdAndStatus(requestId, CandidateStatus.ACCEPTED))
                .willReturn(Optional.empty())

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.NOT_FOUND_CANDIDATE)
            verify(userService, never()).getByIdForUpdate(anyOrNull())
        }

        @Test
        @DisplayName("실패: 수락한 사람이 퇴사했으면 승인으로 이어지지 않는다")
        fun approve_fail_accepted_member_left() {
            // given
            setupApprovableRequest()
            given(candidate.member).willReturn(candidateMember)
            given(candidateMember.workplace).willReturn(workplace)
            given(candidateMember.leftAt).willReturn(now.minusHours(1))

            // when & then
            assertApproveFails(ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE)
            verify(userService, never()).getByIdForUpdate(anyOrNull())
        }

        @Test
        @DisplayName("실패: 수락한 사람이 다른 근무지 소속이면 승인할 수 없다")
        fun approve_fail_accepted_member_other_workplace() {
            // given
            setupApprovableRequest()
            val otherWorkplace = mock<Workplace>()
            given(otherWorkplace.id).willReturn(999L)
            given(candidate.member).willReturn(candidateMember)
            given(candidateMember.workplace).willReturn(otherWorkplace)

            // when & then
            assertApproveFails(ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE)
            verify(userService, never()).getByIdForUpdate(anyOrNull())
        }

        @Test
        @DisplayName("실패: 수락한 사람이 현재 EMPLOYEE가 아니면 승인할 수 없다")
        fun approve_fail_candidate_not_employee() {
            // given
            setupApprovableRequest()
            given(candidate.member).willReturn(candidateMember)
            given(candidateMember.workplace).willReturn(workplace)
            given(candidateMember.leftAt).willReturn(null)

            // 수락 당시에는 EMPLOYEE였지만 승인 시점에는 MANAGER가 된 상황
            given(candidateMember.role).willReturn(WorkplaceRole.MANAGER)

            // when & then
            assertApproveFails(ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE)

            // 승인 대상이 아니므로 User 시간 Lock까지 갈 필요가 없다
            verify(userService, never()).getByIdForUpdate(anyOrNull())
        }

        @Test
        @DisplayName("실패: 근무 시작 시각이 이미 지났으면 승인할 수 없다")
        fun approve_fail_past_shift_start_time() {
            // given
            setupApprovableRequest()
            setupValidCandidateInfo()
            given(shift.startAt).willReturn(now.minusHours(1))

            // when & then
            assertApproveFails(ShiftErrorCode.INVALID_TIME_VALUE)
            verify(shiftRepository, never()).existsConflictingShift(anyOrNull(), anyOrNull(), anyOrNull())
        }

        @Test
        @DisplayName("실패: 근무 시작 시각이 정확히 now(경계값)이면 승인할 수 없다")
        fun approve_fail_shift_starts_exactly_now() {
            // given
            setupApprovableRequest()
            setupValidCandidateInfo()
            given(shift.startAt).willReturn(now)

            // when & then
            assertApproveFails(ShiftErrorCode.INVALID_TIME_VALUE)
        }

        @Test
        @DisplayName("실패: 후보자에게 충돌하는 기존 Shift가 존재하면 승인할 수 없다")
        fun approve_fail_conflicting_shift() {
            // given
            setupApprovableRequest()
            setupValidCandidateInfo()
            given(shift.startAt).willReturn(now.plusDays(1))
            given(shift.endAt).willReturn(now.plusDays(1).plusHours(8))

            given(shiftRepository.existsConflictingShift(candidateUserId, now.plusDays(1), now.plusDays(1).plusHours(8)))
                .willReturn(true)

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.CONFLICT_SHIFT)
        }

        @Test
        @DisplayName("실패: 후보자의 근무 불가 시간과 겹치면 승인할 수 없다")
        fun approve_fail_conflicting_unavailable_time() {
            // given
            setupApprovableRequest()
            setupValidCandidateInfo()
            given(shift.startAt).willReturn(now.plusDays(1))
            given(shift.endAt).willReturn(now.plusDays(1).plusHours(8))

            given(
                unavailableTimeRepository.existsOverlappingUnavailableTime(
                    candidateUserId, now.plusDays(1), now.plusDays(1).plusHours(8),
                )
            ).willReturn(true)

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME)
        }

        @Test
        @DisplayName("실패: 후보자가 다른 활성 대타 약속과 겹치면 승인할 수 없다")
        fun approve_fail_conflicting_active_substitute() {
            // given
            setupApprovableRequest()
            setupValidCandidateInfo()
            given(shift.startAt).willReturn(now.plusDays(1))
            given(shift.endAt).willReturn(now.plusDays(1).plusHours(8))

            given(
                substituteCandidateRepository.existsConflictingActiveSubstitute(
                    candidateUserId, requestId,
                    now.plusDays(1), now.plusDays(1).plusHours(8), now,
                )
            ).willReturn(true)

            // when & then
            assertApproveFails(SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE)
        }
    }

    // ===================== SUB-06/07 진행 중 요청 목록 조회 =====================

    @Nested
    @DisplayName("진행 중인 대타 요청 목록 조회 (list)")
    inner class ListTest {

        @Test
        @DisplayName(
            "성공: OPEN과 ACCEPTED 요청이 섞여있으면, " +
                "ACCEPTED만 acceptedMember가 채워진 DTO 리스트를 반환한다."
        )
        fun list_MixedStatusRequests_ReturnsCorrectDtoList() {
            // given
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace))
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willReturn(mock<WorkplaceMember>())

            val requesterUser = mock<User>()
            given(requesterUser.name).willReturn("요청자")
            given(requesterMember.id).willReturn(requesterMemberId)
            given(requesterMember.user).willReturn(requesterUser)

            given(shift.schedule).willReturn(schedule)
            given(schedule.workplace).willReturn(workplace)
            given(workplace.id).willReturn(workplaceId)
            given(workplace.name).willReturn("테스트 근무지")
            given(shift.id).willReturn(50L)
            given(shift.startAt).willReturn(now.plusDays(1))
            given(shift.endAt).willReturn(now.plusDays(1).plusHours(8))

            val openRequest = mock<SubstituteRequest>()
            given(openRequest.id).willReturn(1L)
            given(openRequest.status).willReturn(RequestStatus.OPEN)
            given(openRequest.shift).willReturn(shift)
            given(openRequest.requesterMember).willReturn(requesterMember)
            given(openRequest.createDate).willReturn(now)

            val acceptedRequest = mock<SubstituteRequest>()
            given(acceptedRequest.id).willReturn(2L)
            given(acceptedRequest.status).willReturn(RequestStatus.ACCEPTED)
            given(acceptedRequest.shift).willReturn(shift)
            given(acceptedRequest.requesterMember).willReturn(requesterMember)
            given(acceptedRequest.createDate).willReturn(now)

            given(substituteRequestRepository.findAllByWorkplaceId(eq(workplaceId), anyOrNull()))
                .willReturn(listOf(openRequest, acceptedRequest))

            given(candidate.request).willReturn(acceptedRequest)
            given(candidate.member).willReturn(candidateMember)
            given(candidateMember.id).willReturn(candidateUserId)
            given(candidateMember.user).willReturn(candidateUser)
            given(candidateUser.name).willReturn("수락자")

            given(substituteCandidateRepository.findByRequestIdInAndStatus(listOf(2L), CandidateStatus.ACCEPTED))
                .willReturn(listOf(candidate))

            // when
            val result = substituteRequestService.list(workplaceId, actorId)

            // then
            assertThat(result).hasSize(2)

            val openResponse = result.first { it.requestId == 1L }
            assertThat(openResponse.acceptedMember).isNull()

            val acceptedResponse = result.first { it.requestId == 2L }
            assertThat(acceptedResponse.acceptedMember).isNotNull()
        }

        @Test
        @DisplayName("실패: ACCEPTED 요청인데 수락자가 없으면 예외가 발생한다.")
        fun list_AcceptedWithoutCandidate_ThrowsException() {
            // given
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace))
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willReturn(mock<WorkplaceMember>())

            val acceptedRequest = mock<SubstituteRequest>()
            given(acceptedRequest.id).willReturn(requestId)
            given(acceptedRequest.status).willReturn(RequestStatus.ACCEPTED)

            given(substituteRequestRepository.findAllByWorkplaceId(eq(workplaceId), anyOrNull()))
                .willReturn(listOf(acceptedRequest))

            given(substituteCandidateRepository.findByRequestIdInAndStatus(listOf(requestId), CandidateStatus.ACCEPTED))
                .willReturn(listOf())

            // when & then
            assertBusinessError(SubstituteRequestErrorCode.ACCEPTED_CANDIDATE_NOT_FOUND) {
                substituteRequestService.list(workplaceId, actorId)
            }
        }

        @Test
        @DisplayName("실패: ACCEPTED 요청에 수락자가 2명 이상이면 예외가 발생한다.")
        fun list_AcceptedWithDuplicateCandidates_ThrowsException() {
            // given
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace))
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willReturn(mock<WorkplaceMember>())

            val acceptedRequest = mock<SubstituteRequest>()
            given(acceptedRequest.id).willReturn(requestId)
            given(acceptedRequest.status).willReturn(RequestStatus.ACCEPTED)

            given(substituteRequestRepository.findAllByWorkplaceId(eq(workplaceId), anyOrNull()))
                .willReturn(listOf(acceptedRequest))

            val candidate2 = mock<SubstituteCandidate>()
            given(candidate.request).willReturn(acceptedRequest)
            given(candidate2.request).willReturn(acceptedRequest)

            given(substituteCandidateRepository.findByRequestIdInAndStatus(listOf(requestId), CandidateStatus.ACCEPTED))
                .willReturn(listOf(candidate, candidate2))

            // when & then
            assertBusinessError(SubstituteRequestErrorCode.ACCEPTED_CANDIDATE_DUPLICATED) {
                substituteRequestService.list(workplaceId, actorId)
            }
        }

        @Test
        @DisplayName(
            "실패: 근무지가 존재하지 않으면 " +
                "BusinessException(WORKPLACE_NOT_FOUND)이 발생한다."
        )
        fun list_NotFoundWorkplace_ThrowsException() {
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.empty())

            assertBusinessError(WorkplaceErrorCode.WORKPLACE_NOT_FOUND) {
                substituteRequestService.list(workplaceId, actorId)
            }
            verify(substituteRequestRepository, never()).findAllByWorkplaceId(anyOrNull(), anyOrNull())
        }

        @Test
        @DisplayName("실패: 매니저 권한이 없으면 예외가 발생한다.")
        fun list_NotManager_ThrowsException() {
            given(workplaceRepository.findById(workplaceId)).willReturn(Optional.of(workplace))
            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willThrow(BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED))

            assertBusinessError(WorkplaceErrorCode.MANAGER_REQUIRED) {
                substituteRequestService.list(workplaceId, actorId)
            }
            // 권한이 없으면 요청 목록을 조회하지 않는다
            verify(substituteRequestRepository, never()).findAllByWorkplaceId(anyOrNull(), anyOrNull())
        }
    }

    // ===================== SUB-08 MANAGER 전체 종료 =====================

    @Nested
    @DisplayName("MANAGER 대타 요청 전체 종료 (close)")
    inner class CloseTest {

        @Test
        @DisplayName("성공: OPEN 상태의 요청을 MANAGER가 종료하면 CLOSED로 변경된다.")
        fun close_OpenRequest_Success() {

            // given
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))

            given(request.shift).willReturn(shift)
            given(shift.schedule).willReturn(schedule)
            given(schedule.workplace).willReturn(workplace)
            given(workplace.id).willReturn(workplaceId)
            given(request.status).willReturn(RequestStatus.OPEN)
            given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
            given(shift.status).willReturn(ShiftStatus.SCHEDULED)
            given(shift.startAt).willReturn(now.plusDays(1))

            // 알림 수신자 설정 추가

            val firstPendingMemberId = 20L
            val secondPendingMemberId = 30L

            val firstPendingCandidate = mock<SubstituteCandidate>()
            val secondPendingCandidate = mock<SubstituteCandidate>()
            val firstPendingMember = mock<WorkplaceMember>()
            val secondPendingMember = mock<WorkplaceMember>()

            // 원래 대타 요청자
            given(request.requesterMember).willReturn(requesterMember)
            given(requesterMember.id).willReturn(requesterMemberId)

            // 첫 번째 PENDING Candidate → Member #20
            given(firstPendingCandidate.member).willReturn(firstPendingMember)
            given(firstPendingMember.id).willReturn(firstPendingMemberId)

            // 두 번째 PENDING Candidate → Member #30
            given(secondPendingCandidate.member).willReturn(secondPendingMember)
            given(secondPendingMember.id).willReturn(secondPendingMemberId)

            /*
             * OPEN 상태이므로
             * 현재 PENDING Candidate만 조회한다.
             */
            given(substituteCandidateRepository.findByRequestIdInAndStatus(listOf(requestId), CandidateStatus.PENDING))
                .willReturn(listOf(firstPendingCandidate, secondPendingCandidate))

            // when
            val result = substituteRequestService.close(requestId, actorId)

            // then
            assertThat(result).isEqualTo(request)

            verify(substituteRequestRepository).findByIdForUpdate(requestId)

            verify(workplaceMemberService).requireManager(actorId, workplaceId)

            /*
             * OPEN 종료에서는 PENDING Candidate만
             * 조회했는지 확인
             */
            verify(substituteCandidateRepository)
                .findByRequestIdInAndStatus(listOf(requestId), CandidateStatus.PENDING)

            // Request 자체는 MANAGER_CLOSED 처리
            verify(request).closeByManager(any<LocalDateTime>())

            // Event 검증

            val eventCaptor = argumentCaptor<SubstituteRequestManagerClosedEvent>()
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture())

            val event = eventCaptor.lastValue

            // 어떤 Request가 종료됐는가?
            assertThat(event.requestId).isEqualTo(requestId)

            /*
             * 요청자 = 15
             * PENDING 후보 = 20, 30
             *
             * 최종 수신자는
             * [15, 20, 30]
             */
            assertThat(event.recipientMemberIds)
                .containsExactlyInAnyOrder(requesterMemberId, firstPendingMemberId, secondPendingMemberId)
        }

        @Test
        @DisplayName("성공: ACCEPTED 상태의 요청도 MANAGER가 종료할 수 있다.")
        fun close_AcceptedRequest_Success() {

            // given
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))

            given(request.shift).willReturn(shift)
            given(shift.schedule).willReturn(schedule)
            given(schedule.workplace).willReturn(workplace)
            given(workplace.id).willReturn(workplaceId)
            given(request.status).willReturn(RequestStatus.ACCEPTED)
            given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
            given(shift.status).willReturn(ShiftStatus.SCHEDULED)
            given(shift.startAt).willReturn(now.plusDays(1))

            // ACCEPTED 종료 알림 설정
            val acceptedMemberId = 25L

            // 요청자
            given(request.requesterMember).willReturn(requesterMember)
            given(requesterMember.id).willReturn(requesterMemberId)

            /*
             * getAcceptedCandidates() 내부에서
             *
             * candidate.getRequest().getId()
             *
             * 를 사용하므로 둘 다 설정해야 한다.
             */
            given(request.id).willReturn(requestId)
            given(candidate.request).willReturn(request)

            // ACCEPTED Candidate의 WorkplaceMember
            given(candidate.member).willReturn(candidateMember)
            given(candidateMember.id).willReturn(acceptedMemberId)

            /*
             * ACCEPTED 상태 종료에서는
             * ACCEPTED Candidate만 조회한다.
             */
            given(substituteCandidateRepository.findByRequestIdInAndStatus(listOf(requestId), CandidateStatus.ACCEPTED))
                .willReturn(listOf(candidate))

            // when
            val result = substituteRequestService.close(requestId, actorId)

            // then
            assertThat(result).isEqualTo(request)

            verify(request).closeByManager(any<LocalDateTime>())

            verify(substituteCandidateRepository)
                .findByRequestIdInAndStatus(listOf(requestId), CandidateStatus.ACCEPTED)

            // Event 검증
            val eventCaptor = argumentCaptor<SubstituteRequestManagerClosedEvent>()
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture())

            val event = eventCaptor.lastValue

            assertThat(event.requestId).isEqualTo(requestId)

            assertThat(event.recipientMemberIds)
                .containsExactlyInAnyOrder(requesterMemberId, acceptedMemberId)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 요청이면 예외가 발생한다.")
        fun close_RequestNotFound_ThrowsException() {
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.empty())

            assertBusinessError(SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND) {
                substituteRequestService.close(requestId, actorId)
            }
            verifyNoApplicationEventPublished()
        }

        @Test
        @DisplayName("실패: 실제 Workplace의 MANAGER가 아니면 예외가 발생한다.")
        fun close_NotManager_ThrowsException() {
            givenClosableRequestPath()

            given(workplaceMemberService.requireManager(actorId, workplaceId))
                .willThrow(BusinessException(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER))

            assertCloseFails(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER)
        }

        @Test
        @DisplayName("실패: 요청이 이미 APPROVED 상태면 예외가 발생한다.")
        fun close_AlreadyApproved_ThrowsException() {
            givenClosableRequestPath()

            given(request.status).willReturn(RequestStatus.APPROVED)

            assertCloseFails(SubstituteRequestErrorCode.ALREADY_TERMINATED)
        }

        @Test
        @DisplayName("실패: 요청이 이미 CLOSED 상태면 예외가 발생한다.")
        fun close_AlreadyClosed_ThrowsException() {
            givenClosableRequestPath()

            given(request.status).willReturn(RequestStatus.CLOSED)

            assertCloseFails(SubstituteRequestErrorCode.ALREADY_TERMINATED)
        }

        @Test
        @DisplayName("실패: Schedule이 PUBLISHED 상태가 아니면 예외가 발생한다.")
        fun close_ScheduleNotPublished_ThrowsException() {
            givenClosableRequestPath()

            given(request.status).willReturn(RequestStatus.OPEN)
            given(schedule.status).willReturn(ScheduleStatus.DRAFT)

            assertCloseFails(ScheduleErrorCode.NOT_PUBLISHED)
        }

        @Test
        @DisplayName("실패: Shift가 SCHEDULED 상태가 아니면 예외가 발생한다.")
        fun close_ShiftNotScheduled_ThrowsException() {
            givenClosableRequestPath()

            given(request.status).willReturn(RequestStatus.OPEN)
            given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
            given(shift.status).willReturn(ShiftStatus.CANCELLED)

            assertCloseFails(ShiftErrorCode.INVALID_STATUS_VALUE)
        }

        @Test
        @DisplayName("실패: Shift가 이미 시작된 경우(now >= startAt) 예외가 발생한다.")
        fun close_ShiftAlreadyStarted_ThrowsException() {
            givenClosableRequestPath()

            given(request.status).willReturn(RequestStatus.OPEN)
            given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
            given(shift.status).willReturn(ShiftStatus.SCHEDULED)
            given(shift.startAt).willReturn(now.minusMinutes(1))

            assertCloseFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED_NOT_CLOSE)
        }

        @Test
        @DisplayName("실패: Shift 시작 시각이 정확히 now와 같으면(경계값) 예외가 발생한다.")
        fun close_ShiftStartsExactlyNow_ThrowsException() {
            givenClosableRequestPath()

            given(request.status).willReturn(RequestStatus.OPEN)
            given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
            given(shift.status).willReturn(ShiftStatus.SCHEDULED)
            given(shift.startAt).willReturn(now)

            assertCloseFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED_NOT_CLOSE)
        }

        @Test
        @DisplayName("성공: Candidate 상태는 변경되지 않는다 (요청만 CLOSED 처리).")
        fun close_DoesNotModifyCandidateStatus() {

            // given
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))

            given(request.shift).willReturn(shift)
            given(shift.schedule).willReturn(schedule)
            given(schedule.workplace).willReturn(workplace)
            given(workplace.id).willReturn(workplaceId)
            given(request.status).willReturn(RequestStatus.ACCEPTED)
            given(schedule.status).willReturn(ScheduleStatus.PUBLISHED)
            given(shift.status).willReturn(ShiftStatus.SCHEDULED)
            given(shift.startAt).willReturn(now.plusDays(1))

            val acceptedMemberId = 25L

            // 요청자 Member 조회
            given(request.requesterMember).willReturn(requesterMember)
            given(requesterMember.id).willReturn(requesterMemberId)

            // getAcceptedCandidates()가 Request ID를 사용함
            given(request.id).willReturn(requestId)
            given(candidate.request).willReturn(request)

            // ACCEPTED Candidate의 Member
            given(candidate.member).willReturn(candidateMember)
            given(candidateMember.id).willReturn(acceptedMemberId)

            // ACCEPTED 상태 종료이므로 ACCEPTED Candidate 조회
            given(substituteCandidateRepository.findByRequestIdInAndStatus(listOf(requestId), CandidateStatus.ACCEPTED))
                .willReturn(listOf(candidate))

            // when
            substituteRequestService.close(requestId, actorId)

            // then

            // Manager 종료이므로 승인 처리는 하지 않는다.
            verify(request, never()).approveRequest(anyOrNull(), anyOrNull())

            // Shift 담당자도 바꾸지 않는다.
            verify(shift, never()).changeMember(anyOrNull())

            // Candidate를 새로 ACCEPTED 처리하지 않는다.
            verify(candidate, never()).acceptRequest(any<LocalDateTime>())

            // Candidate를 REJECTED 처리하지도 않는다.
            verify(candidate, never()).reject(any<LocalDateTime>())
        }

        // Request → Shift → Schedule → Workplace 경로까지만 설정 (실패 케이스 공통)
        private fun givenClosableRequestPath() {
            given(substituteRequestRepository.findByIdForUpdate(requestId))
                .willReturn(Optional.of(request))
            given(request.shift).willReturn(shift)
            given(shift.schedule).willReturn(schedule)
            given(schedule.workplace).willReturn(workplace)
            given(workplace.id).willReturn(workplaceId)
        }
    }

    // ===================== SUB-01 대타 요청 생성 =====================

    @Nested
    @DisplayName("대타 요청 생성 (create)")
    inner class CreateTest {

        @Test
        @DisplayName("SUB-01 - 검증을 통과하면 대타 요청이 OPEN으로 저장된다")
        fun create_Success_SavesAsOpen() {
            val newShift = createShift(publishedSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED)

            val candidateMemberId = 30L

            val newCandidateMember = mock<WorkplaceMember>()
            val createdCandidate = mock<SubstituteCandidate>()

            given(newCandidateMember.id).willReturn(candidateMemberId)
            given(createdCandidate.member).willReturn(newCandidateMember)

            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift))
            given(substituteRequestRepository.existsByShift_IdAndStatusIn(SHIFT_ID, ACTIVE_STATUSES))
                .willReturn(false)

            given(
                substituteCandidateService.findCandidates(
                    newShift.schedule.workplace.id,
                    newShift.member.id,
                    newShift,
                )
            ).willReturn(listOf(newCandidateMember))

            given(substituteRequestRepository.save(any<SubstituteRequest>()))
                .willAnswer { invocation ->
                    invocation.getArgument<SubstituteRequest>(0).apply {
                        ReflectionTestUtils.setField(this, "id", SAVED_REQUEST_ID)
                    }
                }

            given(substituteCandidateService.createCandidates(any<SubstituteRequest>(), anyOrNull()))
                .willReturn(listOf(createdCandidate))

            val response = substituteRequestService.create(SHIFT_ID, REQUESTER_USER_ID)

            val captor = argumentCaptor<SubstituteRequest>()
            verify(shiftRepository).findByIdForUpdate(SHIFT_ID)
            verify(substituteRequestRepository).save(captor.capture())
            val saved = captor.lastValue
            assertThat(saved.shift).isSameAs(newShift)
            assertThat(saved.requesterMember).isSameAs(newShift.member)
            assertThat(saved.status).isEqualTo(RequestStatus.OPEN)

            assertThat(response.requestCreated).isTrue()
            assertThat(response.requestId).isEqualTo(SAVED_REQUEST_ID)
            assertThat(response.shiftId).isEqualTo(SHIFT_ID)
            assertThat(response.status).isEqualTo(RequestStatus.OPEN)
            assertThat(response.candidateCount).isEqualTo(1)

            val eventCaptor = argumentCaptor<SubstituteRequestCreatedEvent>()
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture())

            val event = eventCaptor.lastValue

            assertThat(event.requestId).isEqualTo(SAVED_REQUEST_ID)
            assertThat(event.candidateMemberIds).containsExactly(candidateMemberId)
        }

        @Test
        @DisplayName("SUB-01 - 대타 후보가 없으면 Request를 생성하지 않는다")
        fun create_NoCandidate_DoesNotCreateRequest() {

            // given
            val newShift = createShift(publishedSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED)

            // 실제 DB에서 조회한 Shift/Workplace라면 ID가 존재하지만,
            // 테스트 Fixture는 직접 만든 Entity이므로 ID를 지정해준다.
            ReflectionTestUtils.setField(newShift, "id", SHIFT_ID)
            ReflectionTestUtils.setField(newShift.schedule.workplace, "id", workplaceId)

            val managerMemberId = 30L

            val managerMember = mock<WorkplaceMember>()
            given(managerMember.id).willReturn(managerMemberId)

            // 기존 Shift 조회
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift))

            // 기존 활성 Request 없음
            given(substituteRequestRepository.existsByShift_IdAndStatusIn(SHIFT_ID, ACTIVE_STATUSES))
                .willReturn(false)

            // Candidate 없음
            given(
                substituteCandidateService.findCandidates(
                    newShift.schedule.workplace.id,
                    newShift.member.id,
                    newShift,
                )
            ).willReturn(listOf())

            // Candidate가 없을 때 현재 MANAGER 조회
            given(workplaceMemberRepository.findAllByWorkplace_IdAndRoleAndLeftAtIsNull(workplaceId, WorkplaceRole.MANAGER))
                .willReturn(listOf(managerMember))

            // when
            val response = substituteRequestService.create(SHIFT_ID, REQUESTER_USER_ID)

            // then - 기존 Response 검증
            assertThat(response.requestCreated).isFalse()
            assertThat(response.requestId).isNull()
            assertThat(response.shiftId).isEqualTo(SHIFT_ID)
            assertThat(response.status).isNull()
            assertThat(response.candidateCount).isZero()

            // Request / Candidate는 여전히 생성되지 않음
            verify(substituteRequestRepository, never()).save(any<SubstituteRequest>())

            verify(substituteCandidateService, never()).createCandidates(any<SubstituteRequest>(), anyOrNull())

            // 현재 MANAGER 조회가 수행됐는지 확인
            verify(workplaceMemberRepository)
                .findAllByWorkplace_IdAndRoleAndLeftAtIsNull(workplaceId, WorkplaceRole.MANAGER)

            // NoCandidate Event 검증
            val eventCaptor = argumentCaptor<SubstituteNoCandidateEvent>()
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture())

            val event = eventCaptor.lastValue

            assertThat(event.shiftId).isEqualTo(SHIFT_ID)
            assertThat(event.managerMemberIds).containsExactly(managerMemberId)
        }

        @Test
        @DisplayName("SUB-01 실패 - 존재하지 않는 Shift")
        fun create_Fail_ShiftNotFound() {
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.empty())

            assertCreateFails(SubstituteRequestErrorCode.SHIFT_NOT_FOUND)
        }

        @Test
        @DisplayName("SUB-01 실패 - 공개되지 않은 근무표의 Shift")
        fun create_Fail_ScheduleNotPublished() {
            val newShift = createShift(draftSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED)
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift))

            assertCreateFails(SubstituteRequestErrorCode.SHIFT_NOT_PUBLISHED)
        }

        @Test
        @DisplayName("SUB-01 실패 - 취소된 Shift")
        fun create_Fail_ShiftCancelled() {
            val newShift = createShift(publishedSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.CANCELLED)
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift))

            assertCreateFails(SubstituteRequestErrorCode.SHIFT_CANCELLED)
        }

        @Test
        @DisplayName("SUB-01 실패 - 본인의 Shift가 아님")
        fun create_Fail_NotOwnShift() {
            val newShift = createShift(publishedSchedule(), OTHER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED)
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift))

            assertCreateFails(SubstituteRequestErrorCode.NOT_OWN_SHIFT)
        }

        @Test
        @DisplayName("SUB-01 실패 - 이미 시작된 Shift")
        fun create_Fail_ShiftAlreadyStarted() {
            val newShift = createShift(publishedSchedule(), REQUESTER_USER_ID, now.minusMinutes(1), ShiftStatus.SCHEDULED)
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift))

            assertCreateFails(SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED)
        }

        @Test
        @DisplayName("SUB-01 실패 - 이미 진행 중인(OPEN/ACCEPTED) 요청이 있음")
        fun create_Fail_ActiveRequestExists() {
            val newShift = createShift(publishedSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED)
            given(shiftRepository.findByIdForUpdate(SHIFT_ID)).willReturn(Optional.of(newShift))
            given(substituteRequestRepository.existsByShift_IdAndStatusIn(SHIFT_ID, ACTIVE_STATUSES))
                .willReturn(true)

            assertCreateFails(SubstituteRequestErrorCode.ACTIVE_REQUEST_EXISTS)
        }

        private fun assertCreateFails(expected: SubstituteRequestErrorCode) {
            assertBusinessError(expected) { substituteRequestService.create(SHIFT_ID, REQUESTER_USER_ID) }

            // 검증에서 막혔다면 후보 조회 → Request/Candidate 저장 → 알림까지 이어지면 안 된다
            verify(substituteCandidateService, never()).findCandidates(anyOrNull(), anyOrNull(), anyOrNull())
            verify(substituteRequestRepository, never()).save(any<SubstituteRequest>())
            verify(substituteCandidateService, never()).createCandidates(anyOrNull(), anyOrNull())
            verifyNoApplicationEventPublished()
        }
    }

    // ===================== SUB-05 보낸 요청 목록 조회 =====================

    @Nested
    @DisplayName("보낸 대타 요청 목록 조회 (getSentRequests)")
    inner class SentRequestsTest {

        @Test
        @DisplayName(
            "SUB-05 - 보낸 요청 목록을 최신순으로 반환하고, " +
                "만료된 요청은 CLOSED/EXPIRED로 표시된다"
        )
        fun getSentRequests_ReturnsLatestFirst_MarksExpired() {
            val openShift = createShift(publishedSchedule(), REQUESTER_USER_ID, futureStartAt(), ShiftStatus.SCHEDULED)
            val openRequest = SubstituteRequest(openShift, openShift.member, RequestStatus.OPEN).apply {
                ReflectionTestUtils.setField(this, "id", 1L)
                ReflectionTestUtils.setField(this, "createDate", now.minusDays(1))
            }

            val expiredShift = createShift(
                publishedSchedule(),
                REQUESTER_USER_ID,
                now.minusMinutes(1),
                ShiftStatus.SCHEDULED,
            )
            val expiredRequest = SubstituteRequest(expiredShift, expiredShift.member, RequestStatus.OPEN).apply {
                ReflectionTestUtils.setField(this, "id", 2L)
                ReflectionTestUtils.setField(this, "createDate", now)
            }

            given(substituteRequestRepository.findAllByRequesterMember_User_IdOrderByCreateDateDesc(REQUESTER_USER_ID))
                .willReturn(listOf(expiredRequest, openRequest))

            val responses = substituteRequestService.getSentRequests(REQUESTER_USER_ID)

            assertThat(responses).hasSize(2)
            assertThat(responses[0].requestId).isEqualTo(2L)
            assertThat(responses[0].status).isEqualTo(RequestStatus.CLOSED)
            assertThat(responses[0].closeReason).isEqualTo(RequestCloseReason.EXPIRED)
            assertThat(responses[1].requestId).isEqualTo(1L)
            assertThat(responses[1].status).isEqualTo(RequestStatus.OPEN)
        }

        @Test
        @DisplayName("SUB-05 - 보낸 요청이 없으면 빈 리스트를 반환한다")
        fun getSentRequests_NoRequests_ReturnsEmptyList() {
            given(substituteRequestRepository.findAllByRequesterMember_User_IdOrderByCreateDateDesc(REQUESTER_USER_ID))
                .willReturn(listOf())

            val responses = substituteRequestService.getSentRequests(REQUESTER_USER_ID)

            assertThat(responses).isEmpty()
        }
    }

    // ===================== 공용 fixture 헬퍼 =====================

    private fun createShift(
        scheduleFixture: Schedule,
        ownerUserId: Long,
        startAt: LocalDateTime,
        status: ShiftStatus,
    ): Shift {
        val owner = WorkplaceMember(
            scheduleFixture.workplace,
            User(ownerUserId, "owner@example.com", "담당자"),
            WorkplaceRole.EMPLOYEE,
            now.minusDays(30),
            null,
        )

        return Shift(scheduleFixture, owner, startAt, startAt.plusHours(4), status)
    }

    private fun draftSchedule(): Schedule =
        Schedule(Workplace("SWITCH 카페", "INVITE01"), LocalDate.now(clock))

    private fun publishedSchedule(): Schedule =
        draftSchedule().apply { publish(now) }

    private fun futureStartAt(): LocalDateTime = now.plusDays(1)

    companion object {
        private const val SHIFT_ID = 1L
        private const val REQUESTER_USER_ID = 10L
        private const val OTHER_USER_ID = 20L
        private const val SAVED_REQUEST_ID = 50L
        private val ACTIVE_STATUSES = listOf(RequestStatus.OPEN, RequestStatus.ACCEPTED)
    }
}
