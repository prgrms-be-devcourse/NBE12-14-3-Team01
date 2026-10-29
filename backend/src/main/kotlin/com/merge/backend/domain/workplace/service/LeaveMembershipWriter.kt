package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.workplace.entity.WorkplaceMember
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * HTTP 진입점이 없는 최종 V2 쓰기 부품. 전체 조정자가 V3/V4 처리와 검증을 끝낸 뒤 호출한다.
 * Transaction을 새로 만들지 않으며, Workplace를 뒤늦게 잠가 전역 순서를 뒤집지 않는다.
 */
@Service
class LeaveMembershipWriter(
    private val entityManager: EntityManager,
    private val workplaceService: WorkplaceService
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun complete(member: WorkplaceMember, endedAt: LocalDateTime, regenerateInviteCode: Boolean): Completion {
        check(!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            "퇴사 완료는 쓰기 Transaction에서만 호출할 수 있습니다."
        }
        requireWriteLock(member)
        // 이미 보유한 소속 Lock 아래에서 1차 캐시의 종료 상태·관계를 갱신한다.
        entityManager.refresh(member)
        val workplace = member.workplace
        if (regenerateInviteCode) requireWriteLock(workplace)
        member.endMembership(endedAt)
        val code = if (regenerateInviteCode) {
            entityManager.refresh(workplace)
            workplaceService.createInviteCode(workplace.inviteCode).also(workplace::regenerateInviteCode)
        } else null
        return Completion(endedAt, code)
    }

    private fun requireWriteLock(entity: Any) {
        check(entityManager.contains(entity) && entityManager.getLockMode(entity) in setOf(
            LockModeType.PESSIMISTIC_WRITE, LockModeType.PESSIMISTIC_FORCE_INCREMENT
        )) { "전체 퇴사 Transaction에서 미리 획득한 관리 Entity의 쓰기 Lock이 필요합니다." }
    }

    data class Completion(val leftAt: LocalDateTime, val newInviteCode: String?)
}
