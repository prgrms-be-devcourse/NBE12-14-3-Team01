package com.merge.backend.domain.substitute.controller

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.exception.ScheduleErrorCode
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.substitute.dto.AcceptedClosedSubstituteRequestResponse
import com.merge.backend.domain.substitute.dto.SentSubstituteRequestResponse
import com.merge.backend.domain.substitute.dto.SubstituteRequestCreateResponse
import com.merge.backend.domain.substitute.dto.SubstituteRequestListResponse
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.substitute.service.SubstituteCandidateService
import com.merge.backend.domain.substitute.service.SubstituteRequestService
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.ErrorCode
import com.merge.backend.global.exception.GlobalExceptionHandler
import com.merge.backend.global.rq.Rq
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.JsonNodeType

// 대타 API 9개의 HTTP 응답 형식(Status·Envelope·data Field)을 고정한다. 인증 Filter는 거치지 않는다
@WebMvcTest(SubstituteController::class, SubstituteRequestController::class, SubstituteCandidateController::class)
@ContextConfiguration(
    classes = [
        SubstituteController::class,
        SubstituteRequestController::class,
        SubstituteCandidateController::class,
        GlobalExceptionHandler::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
class SubstituteControllerContractTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @MockitoBean private lateinit var substituteRequestService: SubstituteRequestService
    @MockitoBean private lateinit var substituteCandidateService: SubstituteCandidateService
    @MockitoBean private lateinit var rq: Rq

    @BeforeEach
    fun setUpActor() {
        whenever(rq.actorId).thenReturn(ACTOR_ID)
    }

    @Test
    @DisplayName("대타 요청을 만들면 201과 code \"201\"을 돌려준다")
    fun create_CandidatesExist_Returns201() {
        // given
        whenever(substituteRequestService.create(SHIFT_ID, ACTOR_ID))
            .thenReturn(SubstituteRequestCreateResponse(true, REQUEST_ID, SHIFT_ID, RequestStatus.OPEN, 2))

        // when
        val root = perform(post("/shifts/$SHIFT_ID/substitute-requests"), 201)

        // then
        assertSuccess(root, "201", "대타 요청이 생성되었습니다.")
        val data = root.get("data")
        assertThat(fieldNames(data)).containsExactlyInAnyOrder(
            "requestCreated", "requestId", "shiftId", "status", "candidateCount",
        )
        assertThat(data.get("requestCreated").booleanValue()).isTrue()
        assertThat(data.get("requestId").longValue()).isEqualTo(REQUEST_ID)
        assertThat(data.get("shiftId").longValue()).isEqualTo(SHIFT_ID)
        assertThat(data.get("status").stringValue()).isEqualTo("OPEN")
        assertThat(data.get("candidateCount").intValue()).isEqualTo(2)
    }

    @Test
    @DisplayName("후보가 없으면 200과 code \"200\"을 돌려주고 requestId·status는 null로 나온다")
    fun create_NoCandidate_Returns200WithNullFields() {
        // given
        whenever(substituteRequestService.create(SHIFT_ID, ACTOR_ID))
            .thenReturn(SubstituteRequestCreateResponse(false, null, SHIFT_ID, null, 0))

        // when
        val root = perform(post("/shifts/$SHIFT_ID/substitute-requests"), 200)

        // then
        assertSuccess(root, "200", "현재 대타 요청을 보낼 수 있는 후보가 없습니다.")
        val data = root.get("data")
        assertThat(fieldNames(data)).containsExactlyInAnyOrder(
            "requestCreated", "requestId", "shiftId", "status", "candidateCount",
        )
        assertThat(data.get("requestCreated").booleanValue()).isFalse()
        assertThat(data.get("requestId").nodeType).isEqualTo(JsonNodeType.NULL)
        assertThat(data.get("status").nodeType).isEqualTo(JsonNodeType.NULL)
        assertThat(data.get("candidateCount").intValue()).isZero()
    }

    @Test
    @DisplayName("보낸 대타 요청 목록은 날짜를 시간대 없는 문자열로, 비어 있는 값은 null로 돌려준다")
    fun getSentRequests_ReturnsFieldsAndNulls() {
        // given
        whenever(substituteRequestService.getSentRequests(ACTOR_ID)).thenReturn(
            listOf(
                SentSubstituteRequestResponse(
                    REQUEST_ID, SHIFT_ID, WORKPLACE_ID, WORKPLACE_NAME, SHIFT_START, SHIFT_END,
                    RequestStatus.APPROVED, null, CREATED_AT, NOW, NOW,
                ),
                SentSubstituteRequestResponse(
                    REQUEST_ID + 1, SHIFT_ID + 1, WORKPLACE_ID, WORKPLACE_NAME, SHIFT_START, SHIFT_END,
                    RequestStatus.OPEN, null, CREATED_AT, null, null,
                ),
            )
        )

        // when
        val root = perform(get("/substitute-requests/sent"), 200)

        // then
        assertSuccess(root, "200", "보낸 대타 요청 목록을 조회했습니다.")
        val data = root.get("data")
        assertThat(data.nodeType).isEqualTo(JsonNodeType.ARRAY)
        assertThat(data.size()).isEqualTo(2)
        data.forEach {
            assertThat(fieldNames(it)).containsExactlyInAnyOrder(
                "requestId", "shiftId", "workplaceId", "workplaceName", "startAt", "endAt",
                "status", "closeReason", "createdAt", "approvedAt", "closedAt",
            )
        }
        val approved = data.get(0)
        assertThat(approved.get("status").stringValue()).isEqualTo("APPROVED")
        assertThat(approved.get("startAt").stringValue()).isEqualTo("2026-09-25T09:00:00")
        assertThat(approved.get("endAt").stringValue()).isEqualTo("2026-09-25T13:00:00")
        assertThat(approved.get("createdAt").stringValue()).isEqualTo("2026-09-20T10:00:00")
        assertThat(approved.get("approvedAt").stringValue()).isEqualTo("2026-09-21T12:00:00")
        assertThat(approved.get("closeReason").nodeType).isEqualTo(JsonNodeType.NULL)
        val open = data.get(1)
        assertThat(open.get("approvedAt").nodeType).isEqualTo(JsonNodeType.NULL)
        assertThat(open.get("closedAt").nodeType).isEqualTo(JsonNodeType.NULL)
    }

    @Test
    @DisplayName("받은 대타 요청 목록은 내 Candidate ID와 요청·근무 정보를 돌려준다")
    fun getReceivedRequests_ReturnsFields() {
        // given
        val candidate = candidate(request(RequestStatus.OPEN))
        whenever(substituteCandidateService.findReceivedRequests(ACTOR_ID)).thenReturn(listOf(candidate))

        // when
        val root = perform(get("/substitute-requests/received"), 200)

        // then
        assertSuccess(root, "200", "응답 가능한 대타 요청을 조회했습니다.")
        val item = root.get("data").single()
        assertThat(fieldNames(item)).containsExactlyInAnyOrder(
            "candidateId", "requestId", "shiftId", "workplaceId", "workplaceName", "requesterMemberId",
            "requesterName", "startAt", "endAt", "requestStatus", "candidateStatus", "createdAt",
        )
        assertThat(item.get("candidateId").longValue()).isEqualTo(CANDIDATE_ID)
        assertThat(item.get("requesterMemberId").longValue()).isEqualTo(REQUESTER_MEMBER_ID)
        assertThat(item.get("requesterName").stringValue()).isEqualTo(REQUESTER_NAME)
        assertThat(item.get("workplaceName").stringValue()).isEqualTo(WORKPLACE_NAME)
        assertThat(item.get("requestStatus").stringValue()).isEqualTo("OPEN")
        assertThat(item.get("candidateStatus").stringValue()).isEqualTo("PENDING")
        assertThat(item.get("createdAt").stringValue()).isEqualTo("2026-09-20T10:00:00")
    }

    @Test
    @DisplayName("수락한 승인 대기 목록은 응답 시각을 포함해 돌려준다")
    fun getAcceptedPendingRequests_ReturnsFields() {
        // given
        val candidate = candidate(request(RequestStatus.OPEN)).apply { acceptRequest(NOW) }
        whenever(substituteCandidateService.findAcceptedPendingRequests(ACTOR_ID)).thenReturn(listOf(candidate))

        // when
        val root = perform(get("/substitute-requests/accepted"), 200)

        // then
        assertSuccess(root, "200", "승인 대기 중인 대타 요청을 조회했습니다.")
        val item = root.get("data").single()
        assertThat(fieldNames(item)).containsExactlyInAnyOrder(
            "candidateId", "requestId", "shiftId", "workplaceId", "workplaceName", "requesterName",
            "startAt", "endAt", "requestStatus", "candidateStatus", "respondedAt",
        )
        assertThat(item.get("requestStatus").stringValue()).isEqualTo("ACCEPTED")
        assertThat(item.get("candidateStatus").stringValue()).isEqualTo("ACCEPTED")
        assertThat(item.get("respondedAt").stringValue()).isEqualTo("2026-09-21T12:00:00")
    }

    @Test
    @DisplayName("종료된 대타 목록(API-SB-01)은 종료 사유와 효력 종료 시각을 돌려준다")
    fun getAcceptedClosedRequests_ReturnsFields() {
        // given
        whenever(substituteCandidateService.findAcceptedClosedRequests(ACTOR_ID)).thenReturn(
            listOf(
                AcceptedClosedSubstituteRequestResponse(
                    REQUEST_ID, WORKPLACE_ID, WORKPLACE_NAME, REQUESTER_NAME, SHIFT_START, SHIFT_END,
                    RequestStatus.CLOSED, RequestCloseReason.MANAGER_CLOSED, CandidateStatus.ACCEPTED, NOW,
                ),
                AcceptedClosedSubstituteRequestResponse(
                    REQUEST_ID + 1, WORKPLACE_ID, WORKPLACE_NAME, REQUESTER_NAME, SHIFT_START, SHIFT_END,
                    RequestStatus.APPROVED, null, CandidateStatus.ACCEPTED, NOW,
                ),
            )
        )

        // when
        val root = perform(get("/substitute-requests/accepted/closed"), 200)

        // then
        assertSuccess(root, "200", "종료된 대타 요청을 조회했습니다.")
        val data = root.get("data")
        assertThat(data.size()).isEqualTo(2)
        data.forEach {
            assertThat(fieldNames(it)).containsExactlyInAnyOrder(
                "requestId", "workplaceId", "workplaceName", "requesterName", "startAt", "endAt",
                "requestStatus", "closeReason", "candidateStatus", "closedAt",
            )
        }
        assertThat(data.get(0).get("closeReason").stringValue()).isEqualTo("MANAGER_CLOSED")
        assertThat(data.get(0).get("closedAt").stringValue()).isEqualTo("2026-09-21T12:00:00")
        assertThat(data.get(1).get("requestStatus").stringValue()).isEqualTo("APPROVED")
        assertThat(data.get(1).get("closeReason").nodeType).isEqualTo(JsonNodeType.NULL)
    }

    @Test
    @DisplayName("관리자 진행 중 목록은 수락자가 있으면 객체로, 없으면 null로 돌려준다")
    fun list_ReturnsAcceptedMemberObjectOrNull() {
        // given
        whenever(substituteRequestService.list(WORKPLACE_ID, ACTOR_ID)).thenReturn(
            listOf(
                SubstituteRequestListResponse(
                    REQUEST_ID, SHIFT_ID, REQUESTER_MEMBER_ID, REQUESTER_NAME, WORKPLACE_ID, WORKPLACE_NAME,
                    SHIFT_START, SHIFT_END, RequestStatus.ACCEPTED, CREATED_AT,
                    SubstituteRequestListResponse.AcceptedMemberResponse(CANDIDATE_MEMBER_ID, CANDIDATE_NAME),
                ),
                SubstituteRequestListResponse(
                    REQUEST_ID + 1, SHIFT_ID + 1, REQUESTER_MEMBER_ID, REQUESTER_NAME, WORKPLACE_ID, WORKPLACE_NAME,
                    SHIFT_START, SHIFT_END, RequestStatus.OPEN, CREATED_AT, null,
                ),
            )
        )

        // when
        val root = perform(get("/workplaces/$WORKPLACE_ID/substitute-requests/ongoing"), 200)

        // then
        assertSuccess(root, "200", "진행 중인 대타 요청을 조회했습니다.")
        val data = root.get("data")
        data.forEach {
            assertThat(fieldNames(it)).containsExactlyInAnyOrder(
                "requestId", "shiftId", "requesterMemberId", "requesterName", "workplaceId", "workplaceName",
                "startAt", "endAt", "status", "createdAt", "acceptedMember",
            )
        }
        val acceptedMember = data.get(0).get("acceptedMember")
        assertThat(fieldNames(acceptedMember)).containsExactlyInAnyOrder("memberId", "name")
        assertThat(acceptedMember.get("memberId").longValue()).isEqualTo(CANDIDATE_MEMBER_ID)
        assertThat(acceptedMember.get("name").stringValue()).isEqualTo(CANDIDATE_NAME)
        assertThat(data.get(1).get("acceptedMember").nodeType).isEqualTo(JsonNodeType.NULL)
    }

    @Test
    @DisplayName("승인하면 이전·새 담당자와 승인 시각을 돌려준다")
    fun approve_ReturnsPreviousAndNewMember() {
        // given
        val request = request(RequestStatus.OPEN)
        val candidate = candidate(request).apply { acceptRequest(NOW) }
        request.approveRequest(candidate.member, NOW)
        whenever(substituteRequestService.approve(REQUEST_ID, ACTOR_ID)).thenReturn(request)

        // when
        val root = perform(patch("/substitute-requests/$REQUEST_ID/approve"), 200)

        // then
        assertSuccess(root, "200", "대타 요청이 최종 승인되었습니다.")
        val data = root.get("data")
        assertThat(fieldNames(data)).containsExactlyInAnyOrder(
            "requestId", "shiftId", "status", "previousMemberId", "newMemberId", "newMemberName",
            "approvedAt", "closedAt",
        )
        assertThat(data.get("status").stringValue()).isEqualTo("APPROVED")
        assertThat(data.get("previousMemberId").longValue()).isEqualTo(REQUESTER_MEMBER_ID)
        assertThat(data.get("newMemberId").longValue()).isEqualTo(CANDIDATE_MEMBER_ID)
        assertThat(data.get("newMemberName").stringValue()).isEqualTo(CANDIDATE_NAME)
        assertThat(data.get("approvedAt").stringValue()).isEqualTo("2026-09-21T12:00:00")
        assertThat(data.get("closedAt").stringValue()).isEqualTo("2026-09-21T12:00:00")
    }

    @Test
    @DisplayName("관리자가 종료하면 종료 사유와 종료 시각을 돌려준다")
    fun close_ReturnsCloseReason() {
        // given
        val request = request(RequestStatus.OPEN).apply { closeByManager(NOW) }
        whenever(substituteRequestService.close(REQUEST_ID, ACTOR_ID)).thenReturn(request)

        // when
        val root = perform(patch("/substitute-requests/$REQUEST_ID/close"), 200)

        // then
        assertSuccess(root, "200", "대타 요청이 종료되었습니다.")
        val data = root.get("data")
        assertThat(fieldNames(data)).containsExactlyInAnyOrder(
            "requestId", "shiftId", "status", "closeReason", "closedAt",
        )
        assertThat(data.get("status").stringValue()).isEqualTo("CLOSED")
        assertThat(data.get("closeReason").stringValue()).isEqualTo("MANAGER_CLOSED")
        assertThat(data.get("closedAt").stringValue()).isEqualTo("2026-09-21T12:00:00")
    }

    @Test
    @DisplayName("후보가 수락하면 수락 문구와 처리 후 상태를 돌려주고 종료 사유는 null이다")
    fun respond_Accept_ReturnsAcceptedState() {
        // given
        val candidate = candidate(request(RequestStatus.OPEN)).apply { acceptRequest(NOW) }
        whenever(substituteCandidateService.acceptCandidate(CANDIDATE_ID, ACTOR_ID)).thenReturn(candidate)

        // when
        val root = perform(respond("""{"decision":"ACCEPT"}"""), 200)

        // then
        assertSuccess(root, "200", "대타 요청을 수락했습니다.")
        val data = root.get("data")
        assertThat(fieldNames(data)).containsExactlyInAnyOrder(
            "candidateId", "requestId", "candidateStatus", "requestStatus", "respondedAt", "closeReason",
        )
        assertThat(data.get("candidateStatus").stringValue()).isEqualTo("ACCEPTED")
        assertThat(data.get("requestStatus").stringValue()).isEqualTo("ACCEPTED")
        assertThat(data.get("respondedAt").stringValue()).isEqualTo("2026-09-21T12:00:00")
        assertThat(data.get("closeReason").nodeType).isEqualTo(JsonNodeType.NULL)
    }

    @Test
    @DisplayName("마지막 후보가 거절하면 거절 문구와 전원 거절 종료 사유를 돌려준다")
    fun respond_Reject_ReturnsClosedState() {
        // given
        val request = request(RequestStatus.OPEN).apply { closeAllCandidatesRejected(NOW) }
        val candidate = candidate(request).apply { reject(NOW) }
        whenever(substituteCandidateService.rejectCandidate(CANDIDATE_ID, ACTOR_ID)).thenReturn(candidate)

        // when
        val root = perform(respond("""{"decision":"REJECT"}"""), 200)

        // then
        assertSuccess(root, "200", "대타 요청을 거절했습니다.")
        val data = root.get("data")
        assertThat(data.get("candidateStatus").stringValue()).isEqualTo("REJECTED")
        assertThat(data.get("requestStatus").stringValue()).isEqualTo("CLOSED")
        assertThat(data.get("closeReason").stringValue()).isEqualTo("ALL_CANDIDATES_REJECTED")
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", """{"decision":null}"""])
    @DisplayName("응답 값이 없거나 null이면 400 COM-001과 Field 오류를 돌려주고 Service를 부르지 않는다")
    fun respond_MissingDecision_Returns400Com001(body: String) {
        // when
        val root = perform(respond(body), 400)

        // then
        assertThat(fieldNames(root)).containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(root.get("success").booleanValue()).isFalse()
        assertThat(root.get("code").stringValue()).isEqualTo("COM-001")
        assertThat(root.get("message").stringValue()).isEqualTo("요청 값이 올바르지 않습니다.")
        val fieldErrors = root.get("data").get("fieldErrors")
        assertThat(fieldErrors.size()).isEqualTo(1)
        assertThat(fieldErrors.get(0).get("field").stringValue()).isEqualTo("decision")
        verifyNoInteractions(substituteCandidateService)
    }

    @ParameterizedTest(name = "{0} {2} {3}")
    @MethodSource("failures")
    @DisplayName("Service 실패는 Endpoint마다 ErrorCode의 Status·code·message와 data null로 나간다")
    fun failure_ReturnsErrorEnvelope(api: String, error: ErrorCode, status: Int, code: String) {
        // given
        val request = stubFailure(api, BusinessException(error))

        // when
        val root = perform(request, status)

        // then
        assertThat(fieldNames(root)).containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(root.get("success").booleanValue()).isFalse()
        assertThat(root.get("code").stringValue()).isEqualTo(code)
        assertThat(root.get("message").stringValue()).isEqualTo(error.getMessage())
        assertThat(root.get("data").nodeType).isEqualTo(JsonNodeType.NULL)
    }

    // ===================== 헬퍼 =====================

    private fun stubFailure(api: String, exception: BusinessException): MockHttpServletRequestBuilder =
        when (api) {
            "SUB-01" -> {
                whenever(substituteRequestService.create(SHIFT_ID, ACTOR_ID)).thenThrow(exception)
                post("/shifts/$SHIFT_ID/substitute-requests")
            }
            "SUB-06" -> {
                whenever(substituteRequestService.list(WORKPLACE_ID, ACTOR_ID)).thenThrow(exception)
                get("/workplaces/$WORKPLACE_ID/substitute-requests/ongoing")
            }
            "SUB-03" -> {
                whenever(substituteCandidateService.acceptCandidate(CANDIDATE_ID, ACTOR_ID)).thenThrow(exception)
                respond("""{"decision":"ACCEPT"}""")
            }
            "SUB-04" -> {
                whenever(substituteRequestService.approve(REQUEST_ID, ACTOR_ID)).thenThrow(exception)
                patch("/substitute-requests/$REQUEST_ID/approve")
            }
            "SUB-08" -> {
                whenever(substituteRequestService.close(REQUEST_ID, ACTOR_ID)).thenThrow(exception)
                patch("/substitute-requests/$REQUEST_ID/close")
            }
            else -> error("알 수 없는 API: $api")
        }

    private fun respond(body: String): MockHttpServletRequestBuilder =
        patch("/substitute-candidates/$CANDIDATE_ID/response")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun perform(request: MockHttpServletRequestBuilder, expectedStatus: Int): JsonNode {
        val response = mvc.perform(request).andReturn().response
        assertThat(response.status).isEqualTo(expectedStatus)
        return objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8))
    }

    private fun assertSuccess(root: JsonNode, code: String, message: String) {
        assertThat(fieldNames(root)).containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(root.get("success").booleanValue()).isTrue()
        assertThat(root.get("code").stringValue()).isEqualTo(code)
        assertThat(root.get("message").stringValue()).isEqualTo(message)
    }

    private fun fieldNames(node: JsonNode): Set<String> = node.properties().map { it.key }.toSet()

    // 요청자 근무(SHIFT_START~SHIFT_END)에 대한 요청. 후보는 같은 근무지 직원
    private fun request(status: RequestStatus): SubstituteRequest {
        val workplace = Workplace(WORKPLACE_NAME, "INVITE01").apply { setId(WORKPLACE_ID) }
        val schedule = Schedule(workplace, SHIFT_START.toLocalDate()).apply { publish(CREATED_AT) }
        val requester = member(workplace, REQUESTER_MEMBER_ID, 2L, REQUESTER_NAME)
        val shift = Shift(schedule, requester, SHIFT_START, SHIFT_END, ShiftStatus.SCHEDULED).apply { setId(SHIFT_ID) }

        return SubstituteRequest(shift, requester, status).apply {
            setId(REQUEST_ID)
            ReflectionTestUtils.setField(this, "createDate", CREATED_AT)
        }
    }

    private fun candidate(request: SubstituteRequest): SubstituteCandidate {
        val member = member(request.shift.schedule.workplace, CANDIDATE_MEMBER_ID, 3L, CANDIDATE_NAME)
        return SubstituteCandidate(request, member).apply { setId(CANDIDATE_ID) }
    }

    private fun member(workplace: Workplace, memberId: Long, userId: Long, name: String): WorkplaceMember =
        WorkplaceMember(
            workplace, User(userId, "$name@test.com", name), WorkplaceRole.EMPLOYEE, CREATED_AT.minusDays(30),
        ).apply { setId(memberId) }

    private fun Any.setId(id: Long) = ReflectionTestUtils.setField(this, "id", id)

    companion object {
        private const val ACTOR_ID = 1L
        private const val WORKPLACE_ID = 5L
        private const val SHIFT_ID = 10L
        private const val REQUEST_ID = 77L
        private const val CANDIDATE_ID = 30L
        private const val REQUESTER_MEMBER_ID = 20L
        private const val CANDIDATE_MEMBER_ID = 21L
        private const val WORKPLACE_NAME = "SWITCH 카페"
        private const val REQUESTER_NAME = "요청자"
        private const val CANDIDATE_NAME = "후보자"
        private val CREATED_AT: LocalDateTime = LocalDateTime.of(2026, 9, 20, 10, 0)
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 21, 12, 0)
        private val SHIFT_START: LocalDateTime = LocalDateTime.of(2026, 9, 25, 9, 0)
        private val SHIFT_END: LocalDateTime = SHIFT_START.plusHours(4)

        // 지금 대타 API가 던지는 code의 HTTP 응답 (code마다 1번, Endpoint는 그 code를 처음 던지는 곳). Status·code는 지금 값 그대로
        @JvmStatic
        fun failures(): Stream<Arguments> = Stream.of(
            Arguments.of("SUB-01", SubstituteRequestErrorCode.SHIFT_NOT_FOUND, 404, "SUB-009"),
            Arguments.of("SUB-01", SubstituteRequestErrorCode.SHIFT_NOT_PUBLISHED, 400, "SUB-010"),
            Arguments.of("SUB-01", SubstituteRequestErrorCode.SHIFT_CANCELLED, 400, "SUB-014"),
            Arguments.of("SUB-01", SubstituteRequestErrorCode.NOT_OWN_SHIFT, 403, "SUB-011"),
            Arguments.of("SUB-01", SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED, 400, "SUB-012"),
            Arguments.of("SUB-01", SubstituteRequestErrorCode.ACTIVE_REQUEST_EXISTS, 409, "SUB-013"),

            Arguments.of("SUB-06", WorkplaceErrorCode.WORKPLACE_NOT_FOUND, 404, "WPL-005"),
            Arguments.of("SUB-06", WorkplaceErrorCode.NOT_WORKPLACE_MEMBER, 403, "WPL-003"),
            Arguments.of("SUB-06", WorkplaceErrorCode.MANAGER_REQUIRED, 403, "WPL-004"),
            Arguments.of("SUB-06", SubstituteRequestErrorCode.ACCEPTED_CANDIDATE_NOT_FOUND, 404, "SUB-015"),
            Arguments.of("SUB-06", SubstituteRequestErrorCode.ACCEPTED_CANDIDATE_DUPLICATED, 400, "SUB-016"),

            Arguments.of("SUB-03", SubstituteRequestErrorCode.CANDIDATE_NOT_FOUND, 404, "SUB-019"),
            Arguments.of("SUB-03", SubstituteRequestErrorCode.SUBSTITUTE_REQUEST_NOT_FOUND, 404, "SUB-001"),
            Arguments.of("SUB-03", SubstituteRequestErrorCode.NOT_OWN_CANDIDATE, 403, "SUB-020"),
            Arguments.of("SUB-03", SubstituteRequestErrorCode.INVALID_CANDIDATE_MEMBER, 403, "SUB-021"),
            Arguments.of("SUB-03", SubstituteRequestErrorCode.CANDIDATE_ALREADY_RESPONDED, 409, "SUB-022"),
            Arguments.of("SUB-03", SubstituteRequestErrorCode.REQUEST_NOT_OPEN, 409, "SUB-023"),
            Arguments.of("SUB-03", SubstituteRequestErrorCode.CONFLICT_SHIFT, 409, "SUB-005"),
            Arguments.of("SUB-03", SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME, 409, "SUB-006"),
            Arguments.of("SUB-03", SubstituteRequestErrorCode.CONFLICT_ACTIVE_SUBSTITUTE, 409, "SUB-007"),

            Arguments.of("SUB-04", SubstituteRequestErrorCode.INVALID_REQUEST, 400, "SUB-002"),
            Arguments.of("SUB-04", ScheduleErrorCode.NOT_PUBLISHED, 400, "SCH-012"),
            Arguments.of("SUB-04", ShiftErrorCode.IS_CANCELED_SHIFT, 404, "SFT-013"),
            Arguments.of("SUB-04", SubstituteRequestErrorCode.INVALID_MEMBER, 400, "SUB-004"),
            Arguments.of("SUB-04", SubstituteRequestErrorCode.NOT_FOUND_CANDIDATE, 409, "SUB-008"),
            Arguments.of("SUB-04", ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE, 400, "SFT-004"),
            Arguments.of("SUB-04", ShiftErrorCode.INVALID_TIME_VALUE, 400, "SFT-006"),

            Arguments.of("SUB-08", SubstituteRequestErrorCode.ALREADY_TERMINATED, 400, "SUB-017"),
            Arguments.of("SUB-08", ShiftErrorCode.INVALID_STATUS_VALUE, 400, "SFT-005"),
            Arguments.of("SUB-08", SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED_NOT_CLOSE, 400, "SUB-018"),
        )
    }
}
