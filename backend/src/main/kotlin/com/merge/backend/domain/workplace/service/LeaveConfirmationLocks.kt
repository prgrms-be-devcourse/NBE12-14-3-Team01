package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.dto.request.LeaveConfirmRequest
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.global.exception.BusinessException
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class LeaveConfirmationLocks(
    private val entityManager: EntityManager,
    private val members: WorkplaceMemberRepository
) {
    /** 미리보기 원본을 상한으로 잠근다. 빠진 자동 대상은 허용하되 새 대상은 뒤에서 STALE로 끝낸다. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun acquire(
        original: LeavePreviewFacts,
        actorMemberId: Long,
        workplaceId: Long,
        memberId: Long,
        request: LeaveConfirmRequest,
        now: LocalDateTime
    ): Locked {
        if (request.regenerateInviteCode) {
            lock(Workplace::class.java, workplaceId, LockModeType.PESSIMISTIC_WRITE)
                ?: throw BusinessException(WorkplaceErrorCode.WORKPLACE_NOT_FOUND)
        }
        val ownShifts = original.ongoingShifts + original.publishedShifts + original.draftShiftsToDelete
        val schedules = ownShifts.map { it.scheduleId }.distinct().sorted().mapNotNull { id ->
            lock(Schedule::class.java, id, LockModeType.PESSIMISTIC_WRITE)?.let { id }
        }.toSet()
        val shifts = ownShifts.map { it.shiftId }.distinct().sorted().mapNotNull { id ->
            lock(Shift::class.java, id, LockModeType.PESSIMISTIC_WRITE)?.let { id to it }
        }.toMap()

        // Shift Lock 대기 중 생성된 요청도 포함한다. Scalar 조회로 Lock 전 Entity 캐시를 만들지 않는다.
        val discovered = entityManager.createQuery(
            """
            SELECT r.id FROM SubstituteRequest r
            WHERE r.shift.schedule.workplace.id = :workplaceId
              AND r.shift.schedule.status = com.merge.backend.domain.shift.entity.ScheduleStatus.PUBLISHED
              AND r.shift.status = com.merge.backend.domain.shift.entity.ShiftStatus.SCHEDULED
              AND r.shift.startAt > :now
              AND r.status IN (com.merge.backend.domain.substitute.entity.RequestStatus.OPEN,
                               com.merge.backend.domain.substitute.entity.RequestStatus.ACCEPTED)
              AND (r.shift.member.id = :memberId OR EXISTS (
                  SELECT c.id FROM SubstituteCandidate c WHERE c.request = r AND c.member.id = :memberId
                  AND c.status IN (com.merge.backend.domain.substitute.entity.CandidateStatus.PENDING,
                                   com.merge.backend.domain.substitute.entity.CandidateStatus.ACCEPTED)))
            """.trimIndent(), Long::class.javaObjectType
        ).setParameter("workplaceId", workplaceId).setParameter("memberId", memberId)
            .setParameter("now", now).resultList
        val requestIds = (discovered + original.activeRequestsOnPublishedShifts.map { it.requestId } +
            original.candidacies.map { it.requestId }).distinct().sorted().mapNotNull { id ->
            lock(SubstituteRequest::class.java, id, LockModeType.PESSIMISTIC_WRITE)?.let { id }
        }.toSet()

        val targetIds = request.decisions.orEmpty().filterNotNull().filter { it.action == "REASSIGN" }
            .mapNotNull { it.targetMemberId }.distinct()
        val scopes = targetIds.associateWith { id ->
            members.findScopeById(id)?.takeIf { it.workplaceId == workplaceId }
                ?: throw BusinessException(WorkplaceErrorCode.WORKPLACE_MEMBER_NOT_FOUND)
        }
        val userIds = scopes.values.map { requireNotNull(it.userId) }.distinct().sorted()
        userIds.forEach { id ->
            lock(User::class.java, id, LockModeType.PESSIMISTIC_WRITE)
                ?: throw BusinessException(WorkplaceErrorCode.WORKPLACE_MEMBER_NOT_FOUND)
        }
        val lockedMembers = (targetIds + memberId + actorMemberId).distinct().sorted().mapNotNull { id ->
            val mode = if (id == memberId) LockModeType.PESSIMISTIC_WRITE else LockModeType.PESSIMISTIC_READ
            lock(WorkplaceMember::class.java, id, mode)?.let { id to it }
        }.toMap()
        for ((id, scope) in scopes) {
            val member = lockedMembers[id] ?: continue
            if (member.user.id != scope.userId || member.workplace.id != scope.workplaceId) {
                throw BusinessException(WorkplaceErrorCode.STALE_LEAVE_PLAN)
            }
        }

        // Body에 섞인 무관한 Shift의 범위만 조회. 존재 여부와 타 매장은 같은 404로 매핑한다.
        val shiftWorkplaces = request.decisions.orEmpty().filterNotNull().mapNotNull { it.shiftId }
            .distinct().mapNotNull { id ->
                entityManager.createQuery("SELECT s.schedule.workplace.id FROM Shift s WHERE s.id = :id",
                    Long::class.javaObjectType).setParameter("id", id).resultList.firstOrNull()?.let { id to it }
            }.toMap()
        return Locked(schedules, shifts, requestIds, lockedMembers,
            LeavePlanPreparation.References(shiftWorkplaces, targetIds.mapNotNull { id ->
                lockedMembers[id]?.let { id to LeavePlanPreparation.Membership(requireNotNull(it.workplace.id), it.leftAt) }
            }.toMap()))
    }

    private fun <T : Any> lock(type: Class<T>, id: Long, mode: LockModeType): T? {
        val entity = entityManager.find(type, id, mode) ?: return null
        entityManager.refresh(entity, mode)
        return entity
    }

    data class Locked(
        val scheduleIds: Set<Long>,
        val shifts: Map<Long, Shift>,
        val requestIds: Set<Long>,
        val members: Map<Long, WorkplaceMember>,
        val references: LeavePlanPreparation.References
    ) {
        fun covers(current: LeavePreviewFacts): Boolean {
            val currentShifts = current.ongoingShifts + current.publishedShifts + current.draftShiftsToDelete
            val currentRequests = current.activeRequestsOnPublishedShifts.map { it.requestId } +
                current.candidacies.map { it.requestId }
            return currentShifts.all { it.shiftId in shifts && it.scheduleId in scheduleIds } &&
                currentRequests.all { it in requestIds }
        }
    }
}
