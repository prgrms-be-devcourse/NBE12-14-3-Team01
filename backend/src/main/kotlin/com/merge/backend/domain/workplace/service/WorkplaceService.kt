package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.event.WorkplaceMemberJoinedEvent
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.exception.BusinessException
import java.time.Clock
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import org.hibernate.exception.ConstraintViolationException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkplaceService(
    private val clock: Clock,
    private val workplaceRepository: WorkplaceRepository,
    private val workplaceMemberRepository: WorkplaceMemberRepository,
    private val workplaceMemberService: WorkplaceMemberService,
    private val applicationEventPublisher: ApplicationEventPublisher
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

    private fun createInviteCode(previousInviteCode: String? = null): String {
        var inviteCode: String
        do {
            inviteCode = UUID.randomUUID().toString().substring(0, 8)
                .uppercase(Locale.getDefault())
        } while (inviteCode == previousInviteCode || workplaceRepository.existsByInviteCode(inviteCode))

        return inviteCode
    }

    @Transactional
    fun joinWorkplace(inviteCode: String?, user: User?): WorkplaceMember {
        // Entity를 먼저 읽지 않아 Lock 대기 전의 코드가 영속성 Context에 남지 않게 한다.
        val workplaceId = workplaceRepository.findIdByInviteCode(inviteCode)
            ?: throw BusinessException(WorkplaceErrorCode.INVALID_INVITE_CODE)
        val workplace = workplaceRepository.findByIdForUpdate(workplaceId)
            .orElseThrow { BusinessException(WorkplaceErrorCode.INVALID_INVITE_CODE) }

        // 기존 코드 조회의 DB 비교 규칙을 유지한다. 현재 READ_COMMITTED 설정에서
        // Lock 후 다시 조회하므로 대기 중 재발급된 이전 코드는 이 근무지를 가리키지 않는다.
        if (workplaceRepository.findIdByInviteCode(inviteCode) != workplaceId) {
            throw BusinessException(WorkplaceErrorCode.INVALID_INVITE_CODE)
        }

        val joiningUser = requireNotNull(user)
        if (workplaceMemberRepository
                .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(workplaceId, joiningUser.id)
                .isPresent
        ) {
            throw BusinessException(WorkplaceErrorCode.ALREADY_JOINED_WORKPLACE)
        }

        val joinedAt = LocalDateTime.now(clock)
        val member = WorkplaceMember(
            workplace,
            joiningUser,
            WorkplaceRole.EMPLOYEE,
            joinedAt
        )

        val joined = try {
            // MySQL의 즉시 제약 위반을 이 업무 Transaction 안에서 확인한다.
            workplaceMemberRepository.saveAndFlush(member)
        } catch (e: DataIntegrityViolationException) {
            val constraint = generateSequence<Throwable>(e) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .firstOrNull()
            val constraintName = constraint?.constraintName
                ?.substringAfterLast('.')
                ?.trim('`', '"')
            if (constraintName == "uk_u2_active_membership") {
                // 런타임 예외를 다시 던져 실패한 Transaction을 롤백한다.
                throw BusinessException(WorkplaceErrorCode.ALREADY_JOINED_WORKPLACE)
            }
            throw e
        }

        // 현재 관리자에게 가입·재가입 알림 (WORKPLACE_MEMBER_JOINED)
        applicationEventPublisher.publishEvent(WorkplaceMemberJoinedEvent(workplaceId, joined?.id))

        return joined
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

    @Transactional
    fun regenerateInviteCode(workplaceId: Long, actorUserId: Long): Workplace {
        // 새 API의 범위 밖 근무지는 없는 근무지와 같은 404로 처리한다.
        val actor = workplaceMemberRepository
            .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(workplaceId, actorUserId)
            .orElseThrow { BusinessException(WorkplaceErrorCode.WORKPLACE_NOT_FOUND) }
        if (actor.role != WorkplaceRole.MANAGER) {
            throw BusinessException(WorkplaceErrorCode.MANAGER_REQUIRED)
        }

        val workplace = workplaceRepository.findByIdForUpdate(workplaceId)
            .orElseThrow { BusinessException(WorkplaceErrorCode.WORKPLACE_NOT_FOUND) }
        val newInviteCode = createInviteCode(workplace.inviteCode)
        workplace.regenerateInviteCode(newInviteCode)

        return workplace
    }
}
