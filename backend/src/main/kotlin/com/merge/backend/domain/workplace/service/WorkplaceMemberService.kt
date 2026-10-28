package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.global.exception.BusinessException
import org.springframework.stereotype.Service

@Service
class WorkplaceMemberService(
    private val workplaceMemberRepository: WorkplaceMemberRepository
) {
    fun requireManager(actorUserId: Long?, workplaceId: Long?): WorkplaceMember {
        val member = workplaceMemberRepository
            .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(workplaceId, actorUserId)
            .orElseThrow {
                BusinessException(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER)
            }

        if (member.role != WorkplaceRole.MANAGER) {
            throw BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED)
        }

        return member
    }

    /** 권한 → 근무지 범위 → 대상 상태 순서로 검사하며 소속을 변경하지 않는다. */
    fun requireLeaveTarget(actorUserId: Long, workplaceId: Long, memberId: Long): WorkplaceMember {
        requireManager(actorUserId, workplaceId)

        val member = workplaceMemberRepository.findByIdAndWorkplace_Id(memberId, workplaceId)
            .orElseThrow { BusinessException(WorkplaceErrorCode.WORKPLACE_MEMBER_NOT_FOUND) }
        if (member.leftAt != null) {
            throw BusinessException(WorkplaceErrorCode.MEMBERSHIP_ALREADY_ENDED)
        }
        if (member.role != WorkplaceRole.EMPLOYEE) {
            throw BusinessException(WorkplaceErrorCode.MANAGER_CANNOT_LEAVE)
        }
        return member
    }
}
