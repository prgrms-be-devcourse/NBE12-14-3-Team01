package com.merge.backend.domain.workplace.controller

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.service.WorkplaceService
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.GlobalExceptionHandler
import com.merge.backend.global.rq.Rq
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(WorkplaceController::class)
@ContextConfiguration(classes = [
    WorkplaceController::class,
    GlobalExceptionHandler::class
])
@AutoConfigureMockMvc(addFilters = false)
class WorkplaceControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var workplaceService: WorkplaceService

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var rq: Rq

    @Test
    @DisplayName("근무지 생성 시 201과 관리자 정보를 반환한다")
    fun createWorkplace() {
        val user = mock<User>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()

        whenever(rq.actorId).thenReturn(1L)
        whenever(userService.getById(1L)).thenReturn(user)

        whenever(workplaceService.createWorkplace("테스트 매장", user))
            .thenReturn(member)

        whenever(member.workplace).thenReturn(workplace)
        whenever(member.role).thenReturn(WorkplaceRole.MANAGER)
        whenever(workplace.id).thenReturn(10L)
        whenever(workplace.name).thenReturn("테스트 매장")

        mvc.perform(
            post("/workplaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": "테스트 매장"
                        }
                        """.trimIndent() + "\n")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("201"))
            .andExpect(jsonPath("$.data.workplaceId").value(10))
            .andExpect(jsonPath("$.data.name").value("테스트 매장"))
            .andExpect(jsonPath("$.data.role").value("MANAGER"))
    }

    @Test
    @DisplayName("근무지 이름이 공백이면 400을 반환한다")
    fun rejectBlankWorkplaceName() {
        mvc.perform(
            post("/workplaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": "   "
                        }
                        """.trimIndent() + "\n")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-001"))

        verifyNoInteractions(userService, workplaceService)
    }

    @Test
    @DisplayName("초대코드로 참여하면 201과 직원 정보를 반환한다")
    fun joinWorkplace() {
        val user = mock<User>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()

        whenever(rq.actorId).thenReturn(1L)
        whenever(userService.getById(1L)).thenReturn(user)

        whenever(workplaceService.joinWorkplace("ABCD1234", user))
            .thenReturn(member)

        whenever(member.workplace).thenReturn(workplace)
        whenever(member.role).thenReturn(WorkplaceRole.EMPLOYEE)
        whenever(workplace.id).thenReturn(10L)
        whenever(workplace.name).thenReturn("테스트 매장")

        mvc.perform(
            post("/workplaces/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "inviteCode": "ABCD1234"
                        }
                        """.trimIndent() + "\n")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("201"))
            .andExpect(jsonPath("$.data.workplaceId").value(10))
            .andExpect(jsonPath("$.data.role").value("EMPLOYEE"))
            .andExpect(jsonPath("$.data.name").value("테스트 매장"))
    }

    @Test
    @DisplayName("초대코드가 비어 있으면 400을 반환한다")
    fun rejectBlankInviteCode() {
        mvc.perform(
            post("/workplaces/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "inviteCode": ""
                        }
                        """.trimIndent() + "\n")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-001"))

        verifyNoInteractions(userService, workplaceService)
    }

    @Test
    @DisplayName("내 근무지 목록을 200으로 반환한다")
    fun getMyWorkplaces() {
        val user = mock<User>()
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()

        whenever(rq.actorId).thenReturn(1L)
        whenever(userService.getById(1L)).thenReturn(user)

        whenever(workplaceService.getMyWorkplaces(user))
            .thenReturn(listOf(member))

        whenever(member.workplace).thenReturn(workplace)
        whenever(member.role).thenReturn(WorkplaceRole.MANAGER)
        whenever(workplace.id).thenReturn(10L)
        whenever(workplace.name).thenReturn("테스트 매장")

        mvc.perform(get("/workplaces"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].workplaceId").value(10))
            .andExpect(jsonPath("$.data[0].name").value("테스트 매장"))
            .andExpect(jsonPath("$.data[0].role").value("MANAGER"))
    }

    @Test
    @DisplayName("근무지 구성원 목록을 200으로 반환한다")
    fun getWorkplaceMembers() {
        val memberUser = mock<User>()
        val member = mock<WorkplaceMember>()

        whenever(rq.actorId).thenReturn(1L)

        whenever(workplaceService.getWorkplaceMembers(10L, 1L))
            .thenReturn(listOf(member))

        whenever(member.id).thenReturn(100L)
        whenever(member.user).thenReturn(memberUser)
        whenever(member.role).thenReturn(WorkplaceRole.EMPLOYEE)

        whenever(memberUser.name).thenReturn("홍길동")
        whenever(memberUser.email).thenReturn("test@test.com")

        mvc.perform(get("/workplaces/10/members"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].memberId").value(100))
            .andExpect(jsonPath("$.data[0].name").value("홍길동"))
            .andExpect(jsonPath("$.data[0].email").value("test@test.com"))
            .andExpect(jsonPath("$.data[0].role").value("EMPLOYEE"))
    }

    @Test
    @DisplayName("관리자는 초대코드를 200으로 조회한다")
    fun getInviteCode() {
        val workplace = mock<Workplace>()

        whenever(rq.actorId).thenReturn(1L)

        whenever(workplaceService.getInviteCode(10L, 1L))
            .thenReturn(workplace)

        whenever(workplace.id).thenReturn(10L)
        whenever(workplace.inviteCode).thenReturn("ABCD1234")

        mvc.perform(get("/workplaces/10/invite-code"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.workplaceId").value(10))
            .andExpect(jsonPath("$.data.inviteCode").value("ABCD1234"))
    }

    @Test
    @DisplayName("관리자 권한이 없으면 403을 반환한다")
    fun rejectUnauthorizedMembers() {
        whenever(rq.actorId).thenReturn(1L)

        whenever(workplaceService.getWorkplaceMembers(10L, 1L))
            .thenThrow(BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED))

        mvc.perform(get("/workplaces/10/members"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("WPL-004"))
    }

    @Test
    @DisplayName("잘못된 초대코드로 참여하면 404를 반환한다")
    fun rejectInvalidInviteCode() {
        val user = mock<User>()

        whenever(rq.actorId).thenReturn(1L)
        whenever(userService.getById(1L)).thenReturn(user)

        whenever(workplaceService.joinWorkplace("INVALID", user))
            .thenThrow(BusinessException(WorkplaceErrorCode.INVALID_INVITE_CODE))

        mvc.perform(
            post("/workplaces/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "inviteCode": "INVALID"
                        }
                        """.trimIndent() + "\n")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("WPL-001"))
    }

    @Test
    @DisplayName("이미 참여한 근무지에 다시 참여하면 409를 반환한다")
    fun rejectDuplicateJoin() {
        val user = mock<User>()

        whenever(rq.actorId).thenReturn(1L)
        whenever(userService.getById(1L)).thenReturn(user)

        whenever(workplaceService.joinWorkplace("ABCD1234", user))
            .thenThrow(BusinessException(WorkplaceErrorCode.ALREADY_JOINED_WORKPLACE))

        mvc.perform(
            post("/workplaces/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "inviteCode": "ABCD1234"
                        }
                        """.trimIndent() + "\n")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("WPL-002"))
    }

    @Test
    @DisplayName("존재하지 않는 근무지의 구성원 조회 시 404를 반환한다")
    fun rejectMissingWorkplace() {
        whenever(rq.actorId).thenReturn(1L)

        whenever(workplaceService.getWorkplaceMembers(10L, 1L))
            .thenThrow(BusinessException(WorkplaceErrorCode.WORKPLACE_NOT_FOUND))

        mvc.perform(get("/workplaces/10/members"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("WPL-005"))
    }

    @Test
    @DisplayName("근무지 이름 필드가 누락되면 400을 반환한다")
    fun rejectMissingWorkplaceName() {
        mvc.perform(
            post("/workplaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-001"))

        verifyNoInteractions(userService, workplaceService)
    }

    @Test
    @DisplayName("근무지 이름이 null이면 400을 반환한다")
    fun rejectNullWorkplaceName() {
        mvc.perform(
            post("/workplaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": null
                        }
                        """.trimIndent() + "\n")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-001"))

        verifyNoInteractions(userService, workplaceService)
    }

    @Test
    @DisplayName("초대코드 필드가 누락되면 400을 반환한다")
    fun rejectMissingInviteCode() {
        mvc.perform(
            post("/workplaces/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-001"))

        verifyNoInteractions(userService, workplaceService)
    }

    @Test
    @DisplayName("초대코드가 null이면 400을 반환한다")
    fun rejectNullInviteCode() {
        mvc.perform(
            post("/workplaces/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "inviteCode": null
                        }
                        """.trimIndent() + "\n")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-001"))

        verifyNoInteractions(userService, workplaceService)
    }

    @Test
    @DisplayName("JSON 문법이 잘못되면 400과 COM-002를 반환한다")
    fun rejectMalformedJson() {
        mvc.perform(
            post("/workplaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-002"))

        verifyNoInteractions(userService, workplaceService)
    }

    @Test
    @DisplayName("workplaceId가 숫자가 아니면 400을 반환한다")
    fun rejectInvalidWorkplaceIdType() {
        mvc.perform(
            get("/workplaces/abc/members")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-003"))

        verifyNoInteractions(workplaceService)
    }

    @Test
    @DisplayName("소속 근무지가 없으면 빈 배열을 200으로 반환한다")
    fun getEmptyMyWorkplaces() {
        val user = mock<User>()

        whenever(rq.actorId).thenReturn(1L)
        whenever(userService.getById(1L)).thenReturn(user)
        whenever(workplaceService.getMyWorkplaces(user))
            .thenReturn(listOf())

        mvc.perform(get("/workplaces"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(0))
    }

    @Test
    @DisplayName("현재 근무지 구성원이 아니면 403을 반환한다")
    fun rejectNonCurrentMemberAccess() {
        whenever(rq.actorId).thenReturn(1L)

        whenever(workplaceService.getWorkplaceMembers(10L, 1L))
            .thenThrow(BusinessException(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER))

        mvc.perform(get("/workplaces/10/members"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("WPL-003"))
    }

}
