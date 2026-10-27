package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MembershipHistoryService(
    private val workplaceMemberRepository: WorkplaceMemberRepository
) {
    /**
     * actor는 인증 계층에서 확인한 본인이다. 현재 소속이나 관리자 권한은 필요 없다.
     * 이력의 소속 ID를 유지하므로 같은 근무지에 재가입한 기간도 합치지 않는다.
     */
    @Transactional(readOnly = true)
    fun getMyEndedMemberships(actor: User): List<MembershipHistory> {
        val actorUserId = requireNotNull(actor.id) { "영속된 인증 사용자가 필요합니다." }

        return workplaceMemberRepository
            .findAllByUser_IdAndLeftAtIsNotNullOrderByLeftAtDescIdDesc(actorUserId)
            .map { member ->
                MembershipHistory(
                    memberId = requireNotNull(member.id),
                    workplaceName = member.workplace.name,
                    role = member.role,
                    joinedAt = member.joinedAt,
                    leftAt = requireNotNull(member.leftAt)
                )
            }
    }
}
