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
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkplaceService(
    private val clock: Clock,
    private val workplaceRepository: WorkplaceRepository,
    private val workplaceMemberRepository: WorkplaceMemberRepository,
    private val workplaceMemberService: WorkplaceMemberService
) {

    @Transactional
    fun createWorkplace(name: String?, user: User?): WorkplaceMember {
        val inviteCode = createInviteCode()
        val workplace = Workplace(requireNotNull(name), inviteCode)

        workplaceRepository.save(workplace)

        val joinedAt = LocalDateTime.now(clock)
        val member = WorkplaceMember(
            workplace,
            requireNotNull(user),
            WorkplaceRole.MANAGER,
            joinedAt
        )

        return workplaceMemberRepository.save(member)
    }

    private fun createInviteCode(): String {
        var inviteCode: String
        do {
            inviteCode = UUID.randomUUID().toString().substring(0, 8)
                .uppercase(Locale.getDefault())
        } while (workplaceRepository.existsByInviteCode(inviteCode))

        return inviteCode
    }

    @Transactional
    fun joinWorkplace(inviteCode: String?, user: User?): WorkplaceMember {
        val workplace = workplaceRepository.findByInviteCode(inviteCode)
            .orElseThrow { BusinessException(WorkplaceErrorCode.INVALID_INVITE_CODE) }

        if (workplaceMemberRepository.existsByWorkplaceAndUser(workplace, user)) {
            throw BusinessException(WorkplaceErrorCode.ALREADY_JOINED_WORKPLACE)
        }

        val joinedAt = LocalDateTime.now(clock)
        val member = WorkplaceMember(
            workplace,
            requireNotNull(user),
            WorkplaceRole.EMPLOYEE,
            joinedAt
        )

        return workplaceMemberRepository.save(member)
    }

    @Transactional(readOnly = true)
    fun getMyWorkplaces(user: User): List<WorkplaceMember> {
        val userId = user.id
        return workplaceMemberRepository
            .findAllByUser_IdAndLeftAtIsNull(userId)
    }

    @Transactional(readOnly = true)
    fun getWorkplaceMembers(workplaceId: Long?, actorUserId: Long?): List<WorkplaceMember> {
        getWorkplace(workplaceId)

        workplaceMemberService.requireManager(actorUserId, workplaceId)

        return workplaceMemberRepository
            .findAllByWorkplace_IdAndLeftAtIsNull(workplaceId)
    }

    private fun getWorkplace(workplaceId: Long?): Workplace {
        return workplaceRepository.findById(requireNotNull(workplaceId))
            .orElseThrow { BusinessException(WorkplaceErrorCode.WORKPLACE_NOT_FOUND) }
    }

    @Transactional(readOnly = true)
    fun getInviteCode(workplaceId: Long?, actorUserId: Long?): Workplace {
        val workplace = getWorkplace(workplaceId)

        workplaceMemberService.requireManager(actorUserId, workplaceId)

        return workplace
    }
}
