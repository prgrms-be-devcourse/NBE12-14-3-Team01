package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.substitute.service.SubstituteCandidateService
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.global.exception.BusinessException
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class LeaveCandidacyAdapter(
    private val candidates: SubstituteCandidateService,
    private val requests: SubstituteRequestRepository,
    private val entityManager: EntityManager
) : LeaveConfirmationIntegration {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun cleanupCandidacies(
        memberId: Long, now: LocalDateTime, lockedRequestIds: Set<Long>
    ): LeaveConfirmationIntegration.CandidacyCleanup {
        // V4의 조회가 V2보다 넓을 수 있다. ID만 먼저 읽어 잠그지 않은 영향이 있으면 중단한다.
        val activeIds = requests.findActiveIdsByCandidateMemberId(memberId, now).toSet()
        if (!lockedRequestIds.containsAll(activeIds)) {
            throw BusinessException(WorkplaceErrorCode.STALE_LEAVE_PLAN)
        }
        activeIds.forEach { id ->
            val request = entityManager.find(SubstituteRequest::class.java, id)
            check(request != null && entityManager.getLockMode(request) in setOf(
                LockModeType.PESSIMISTIC_WRITE, LockModeType.PESSIMISTIC_FORCE_INCREMENT
            )) { "V4 퇴사 정리에는 V2가 미리 획득한 Request 쓰기 Lock이 필요합니다." }
        }
        val result = candidates.closeCandidaciesByMemberLeave(memberId, now, activeIds)
        return LeaveConfirmationIntegration.CandidacyCleanup(
            result.closedRequests.map { it.requestId }.toSet(), result.ineligibleCandidacies, result.closedRequests
        )
    }
}
