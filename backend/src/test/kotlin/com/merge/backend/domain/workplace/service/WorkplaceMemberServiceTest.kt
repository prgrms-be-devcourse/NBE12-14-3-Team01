package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.global.exception.BusinessException
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class WorkplaceMemberServiceTest {

    @Mock
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @InjectMocks
    private lateinit var workplaceMemberService: WorkplaceMemberService

    @Test
    @DisplayName("현재 MANAGER이면 WorkplaceMember를 반환한다")
    fun allowManager() {
        val actorUserId = 1L
        val workplaceId = 10L
        val member = mock<WorkplaceMember>()

        whenever(workplaceMemberRepository
            .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
                workplaceId, actorUserId
            ))
            .thenReturn(Optional.of(member))

        whenever(member.role)
            .thenReturn(WorkplaceRole.MANAGER)

        val result = workplaceMemberService.requireManager(actorUserId, workplaceId)

        assertSame(member, result)

        verify(workplaceMemberRepository)
            .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
                workplaceId, actorUserId
            )
    }

    @Test
    @DisplayName("EMPLOYEE는 관리자 권한을 얻을 수 없다")
    fun rejectEmployee() {
        val actorUserId = 1L
        val workplaceId = 10L
        val member = mock<WorkplaceMember>()

        whenever(workplaceMemberRepository
            .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
                workplaceId, actorUserId
            ))
            .thenReturn(Optional.of(member))

        whenever(member.role)
            .thenReturn(WorkplaceRole.EMPLOYEE)

        val exception = assertThrows(BusinessException::class.java) {
            workplaceMemberService.requireManager(actorUserId, workplaceId)
        }

        assertSame(
            WorkplaceErrorCode.MANAGER_REQUIRED,
            exception.errorCode
        )
    }

    @Test
    @DisplayName("현재 근무지에 소속되지 않은 사용자는 관리자 권한을 얻을 수 없다")
    fun rejectNonCurrentMember() {
        val actorUserId = 1L
        val workplaceId = 10L

        // 현재 재직 중인 멤버십이 조회되지 않는 상황
        whenever(workplaceMemberRepository
            .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
                workplaceId, actorUserId
            ))
            .thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            workplaceMemberService.requireManager(actorUserId, workplaceId)
        }

        assertSame(
            WorkplaceErrorCode.NOT_WORKPLACE_MEMBER,
            exception.errorCode
        )
    }
}
