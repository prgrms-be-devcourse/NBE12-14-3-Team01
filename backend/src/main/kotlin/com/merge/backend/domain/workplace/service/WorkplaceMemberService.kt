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
}
