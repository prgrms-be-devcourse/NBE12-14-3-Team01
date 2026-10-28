package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.exception.BusinessException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class WorkplaceServiceTest {

    @Mock
    private lateinit var workplaceRepository: WorkplaceRepository

    @Mock
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Mock
    private lateinit var workplaceMemberService: WorkplaceMemberService

    @Mock
    private lateinit var user: User

    private lateinit var workplaceService: WorkplaceService

    // 가입 시간 검증을 위해 시계를 고정 (한국 시간 09:00)
    private val clock: Clock = Clock.fixed(
        Instant.parse("2026-09-16T00:00:00Z"),
        ZoneId.of("Asia/Seoul")
    )

    @BeforeEach
    fun setUp() {
        workplaceService = WorkplaceService(
            clock,
            workplaceRepository,
            workplaceMemberRepository,
            workplaceMemberService
        )
    }

    @Test
    @DisplayName("초대코드로 참여하면 EMPLOYEE 멤버십이 생성된다")
    fun joinAsEmployee() {
        val workplace = Workplace("테스트 매장", "ABCD1234")
        ReflectionTestUtils.setField(workplace, "id", 10L)
        whenever(user.id).thenReturn(1L)

        whenever(workplaceRepository.findIdByInviteCode("ABCD1234"))
            .thenReturn(10L)
        whenever(workplaceRepository.findByIdForUpdate(10L))
            .thenReturn(Optional.of(workplace))
        whenever(workplaceMemberRepository.findByWorkplace_IdAndUser_IdAndLeftAtIsNull(10L, 1L))
            .thenReturn(Optional.empty())

        // 실제 DB 대신 전달받은 멤버십을 반환하도록 Mock 설정
        whenever(workplaceMemberRepository.saveAndFlush(any<WorkplaceMember>()))
            .thenAnswer { invocation -> invocation.getArgument<WorkplaceMember>(0) }

        val result = workplaceService.joinWorkplace("ABCD1234", user)

        assertSame(workplace, result.workplace)
        assertSame(user, result.user)
        assertEquals(WorkplaceRole.EMPLOYEE, result.role)

        assertEquals(
            LocalDateTime.of(2026, 9, 16, 9, 0),
            result.joinedAt
        )

        assertNull(result.leftAt)

        verify(workplaceMemberRepository)
            .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(10L, 1L)

        verify(workplaceMemberRepository).saveAndFlush(result)
    }

    @Test
    @DisplayName("유효하지 않은 초대코드로 참여하면 저장하지 않는다")
    fun rejectInvalidInviteCode() {
        whenever(workplaceRepository.findIdByInviteCode("INVALID"))
            .thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            workplaceService.joinWorkplace("INVALID", user)
        }

        assertSame(
            WorkplaceErrorCode.INVALID_INVITE_CODE,
            exception.errorCode
        )

        verifyNoInteractions(workplaceMemberRepository)
    }

    @Test
    @DisplayName("존재하지 않는 근무지의 구성원은 조회할 수 없다")
    fun rejectMissingWorkplace() {
        whenever(workplaceRepository.findById(10L))
            .thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            workplaceService.getWorkplaceMembers(10L, 1L)
        }

        assertSame(
            WorkplaceErrorCode.WORKPLACE_NOT_FOUND,
            exception.errorCode
        )

        verifyNoInteractions(workplaceMemberService)
        verifyNoInteractions(workplaceMemberRepository)
    }

    @Test
    @DisplayName("근무지를 생성하면 생성자가 MANAGER로 등록된다")
    fun createWorkplaceWithManager() {
        whenever(workplaceRepository.existsByInviteCode(any()))
            .thenReturn(false)

        whenever(workplaceMemberRepository.save(any<WorkplaceMember>()))
            .thenAnswer { invocation -> invocation.getArgument<WorkplaceMember>(0) }

        val result = workplaceService.createWorkplace("테스트 매장", user)

        // Mock 저장소로 전달된 객체를 캡처해 내부 값을 검증
        val workplaceCaptor = argumentCaptor<Workplace>()

        val memberCaptor = argumentCaptor<WorkplaceMember>()

        verify(workplaceRepository).save(workplaceCaptor.capture())
        verify(workplaceMemberRepository).save(memberCaptor.capture())

        val savedWorkplace = workplaceCaptor.firstValue
        val savedMember = memberCaptor.firstValue

        assertEquals("테스트 매장", savedWorkplace.name)

        assertTrue(
            savedWorkplace.inviteCode.matches(Regex("[A-Z0-9]{8}"))
        )

        assertSame(savedWorkplace, savedMember.workplace)
        assertSame(user, savedMember.user)
        assertEquals(WorkplaceRole.MANAGER, savedMember.role)

        assertEquals(
            LocalDateTime.of(2026, 9, 16, 9, 0),
            savedMember.joinedAt
        )

        assertNull(savedMember.leftAt)

        assertSame(savedMember, result)
    }

    @Test
    @DisplayName("이미 참여 중인 사용자는 다시 참여할 수 없다")
    fun rejectDuplicateJoin() {
        val workplace = Workplace("테스트 매장", "ABC123")
        ReflectionTestUtils.setField(workplace, "id", 10L)
        whenever(user.id).thenReturn(1L)
        val currentMember = WorkplaceMember(
            workplace, user, WorkplaceRole.EMPLOYEE, LocalDateTime.now(clock)
        )

        whenever(workplaceRepository.findIdByInviteCode("ABC123"))
            .thenReturn(10L)
        whenever(workplaceRepository.findByIdForUpdate(10L))
            .thenReturn(Optional.of(workplace))

        whenever(workplaceMemberRepository.findByWorkplace_IdAndUser_IdAndLeftAtIsNull(10L, 1L))
            .thenReturn(Optional.of(currentMember))

        val exception = assertThrows(BusinessException::class.java) {
            workplaceService.joinWorkplace("ABC123", user)
        }

        assertSame(
            WorkplaceErrorCode.ALREADY_JOINED_WORKPLACE,
            exception.errorCode
        )

        verify(workplaceMemberRepository, never())
            .saveAndFlush(any<WorkplaceMember>())
    }

    @Test
    @DisplayName("사용자의 소속 Workplace 목록을 조회한다")
    fun getMyWorkplaces() {
        val workplace = mock<Workplace>()
        val member = mock<WorkplaceMember>()

        whenever(user.id).thenReturn(1L)
        whenever(workplaceMemberRepository.findAllByUser_IdAndLeftAtIsNull(1L))
            .thenReturn(listOf(member))

        whenever(member.workplace).thenReturn(workplace)
        whenever(workplace.id).thenReturn(10L)
        whenever(workplace.name).thenReturn("테스트 매장")
        whenever(member.role).thenReturn(WorkplaceRole.MANAGER)

        val response = workplaceService.getMyWorkplaces(user)

        assertEquals(1, response.size)
        assertEquals(10L, response[0].workplace.id)
        assertEquals("테스트 매장", response[0].workplace.name)
        assertEquals(WorkplaceRole.MANAGER, response[0].role)
    }

    @Test
    @DisplayName("소속 Workplace가 없으면 빈 목록을 반환한다")
    fun getEmptyWorkplaces() {
        whenever(user.id).thenReturn(1L)
        whenever(workplaceMemberRepository.findAllByUser_IdAndLeftAtIsNull(1L))
            .thenReturn(listOf())

        val response = workplaceService.getMyWorkplaces(user)

        assertEquals(0, response.size)
    }

    @Test
    @DisplayName("관리자는 Workplace의 현재 멤버 목록을 조회한다")
    fun getCurrentMembers() {
        val workplace = mock<Workplace>()
        val memberUser = mock<User>()
        val member = mock<WorkplaceMember>()

        whenever(workplaceRepository.findById(10L))
            .thenReturn(Optional.of(workplace))

        whenever(workplaceMemberRepository
            .findAllByWorkplace_IdAndLeftAtIsNull(10L))
            .thenReturn(listOf(member))

        whenever(member.id).thenReturn(100L)
        whenever(member.user).thenReturn(memberUser)
        whenever(memberUser.name).thenReturn("홍길동")
        whenever(memberUser.email).thenReturn("test@test.com")
        whenever(member.role).thenReturn(WorkplaceRole.EMPLOYEE)

        val response = workplaceService.getWorkplaceMembers(10L, 1L)

        assertEquals(100L, response[0].id)
        assertEquals("홍길동", response[0].user.name)
        assertEquals("test@test.com", response[0].user.email)
        assertEquals(WorkplaceRole.EMPLOYEE, response[0].role)

        verify(workplaceMemberService).requireManager(1L, 10L)
    }

    @Test
    @DisplayName("관리자는 Workplace의 초대 코드를 조회한다")
    fun getInviteCode() {
        val workplace = mock<Workplace>()

        whenever(workplaceRepository.findById(10L))
            .thenReturn(Optional.of(workplace))
        whenever(workplace.id).thenReturn(10L)
        whenever(workplace.inviteCode).thenReturn("ABCD1234")

        val response = workplaceService.getInviteCode(10L, 1L)

        assertEquals(10L, response.id)
        assertEquals("ABCD1234", response.inviteCode)

        verify(workplaceMemberService).requireManager(1L, 10L)
    }

    @Test
    @DisplayName("관리자 권한 검증 실패 시 구성원 목록을 조회하지 않는다")
    fun rejectUnauthorizedMemberList() {
        val workplace = Workplace("테스트 매장", "ABCD1234")

        whenever(workplaceRepository.findById(10L))
            .thenReturn(Optional.of(workplace))

        whenever(workplaceMemberService.requireManager(1L, 10L))
            .thenThrow(BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED))

        val exception = assertThrows(BusinessException::class.java) {
            workplaceService.getWorkplaceMembers(10L, 1L)
        }

        assertSame(
            WorkplaceErrorCode.MANAGER_REQUIRED,
            exception.errorCode
        )

        verify(workplaceMemberRepository, never())
            .findAllByWorkplace_IdAndLeftAtIsNull(10L)
    }

    @Test
    @DisplayName("중복 초대코드가 생성되면 다시 생성한다")
    fun retryDuplicateInviteCode() {
        // 첫 번째는 중복, 두 번째는 사용 가능하도록 설정
        whenever(workplaceRepository.existsByInviteCode(any()))
            .thenReturn(true, false)

        whenever(workplaceMemberRepository.save(any<WorkplaceMember>()))
            .thenAnswer { invocation -> invocation.getArgument<WorkplaceMember>(0) }

        workplaceService.createWorkplace("테스트 매장", user)

        verify(workplaceRepository, times(2))
            .existsByInviteCode(any())

        verify(workplaceRepository)
            .save(any<Workplace>())

        val codeCaptor = argumentCaptor<String>()

        verify(workplaceRepository, times(2))
            .existsByInviteCode(codeCaptor.capture())

        val checkedCodes = codeCaptor.allValues

        val workplaceCaptor = argumentCaptor<Workplace>()

        verify(workplaceRepository)
            .save(workplaceCaptor.capture())

        val savedWorkplace = workplaceCaptor.firstValue

        // 중복 검사에서 최종 사용한 코드가 실제 저장된 코드여야 한다
        assertEquals(
            checkedCodes[1],
            savedWorkplace.inviteCode
        )

    }

    @Test
    @DisplayName("관리자 권한이 없으면 초대코드를 조회할 수 없다")
    fun rejectUnauthorizedInviteCode() {
        // 관리자 권한 검증 실패 상황
        val workplace = Workplace("테스트 매장", "ABCD1234")

        whenever(workplaceRepository.findById(10L))
            .thenReturn(Optional.of(workplace))

        whenever(workplaceMemberService.requireManager(1L, 10L))
            .thenThrow(BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED))

        val exception = assertThrows(BusinessException::class.java) {
            workplaceService.getInviteCode(10L, 1L)
        }

        assertSame(
            WorkplaceErrorCode.MANAGER_REQUIRED,
            exception.errorCode
        )
    }

    @Test
    @DisplayName("존재하지 않는 근무지의 초대코드는 조회할 수 없다")
    fun rejectMissingWorkplaceInviteCode() {
        whenever(workplaceRepository.findById(999L))
            .thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            workplaceService.getInviteCode(999L, 1L)
        }

        assertSame(
            WorkplaceErrorCode.WORKPLACE_NOT_FOUND,
            exception.errorCode
        )

        // 근무지가 없으면 권한 검증까지 진행하지 않는다
        verifyNoInteractions(workplaceMemberService)
        verifyNoInteractions(workplaceMemberRepository)
    }

    @Test
    @DisplayName("구성원이 없는 근무지는 빈 목록을 반환한다")
    fun getEmptyWorkplaceMembers() {
        val workplace = mock<Workplace>()

        whenever(workplaceRepository.findById(10L))
            .thenReturn(Optional.of(workplace))

        whenever(workplaceMemberRepository
            .findAllByWorkplace_IdAndLeftAtIsNull(10L))
            .thenReturn(listOf())

        val result = workplaceService.getWorkplaceMembers(10L, 1L)

        assertTrue(result.isEmpty())

        // 구성원 목록이 비어 있어도 관리자 권한을 확인한다
        verify(workplaceMemberService)
            .requireManager(1L, 10L)

        verify(workplaceMemberRepository)
            .findAllByWorkplace_IdAndLeftAtIsNull(10L)
    }

}
