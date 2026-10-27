package com.merge.backend.domain.workplace.entity

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.global.entity.BaseEntity
import com.merge.backend.global.exception.BusinessException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["workplace_id", "user_id"])
    ]
)
class WorkplaceMember @JvmOverloads constructor(
    workplace: Workplace,
    user: User,
    role: WorkplaceRole,
    joinedAt: LocalDateTime,
    leftAt: LocalDateTime? = null
) : BaseEntity() {
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "workplace_id", nullable = false)
    var workplace: Workplace = workplace
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "user_id", nullable = false)
    var user: User = user
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var role: WorkplaceRole = role
        protected set

    @field:Column(nullable = false)
    var joinedAt: LocalDateTime = joinedAt
        protected set

    var leftAt: LocalDateTime? = leftAt
        protected set

    fun isCurrent(): Boolean = leftAt == null

    /**
     * 소속 Row만 종료한다. 근무·대타 정리와 Lock 후 재검증을 마친 퇴사 Command가
     * 같은 업무 Transaction 안에서 호출해야 하며, 단독 퇴사 처리로 사용하지 않는다.
     * 이미 종료된 소속과 관리자 소속의 퇴사 요청은 BusinessException으로 거부한다.
     */
    fun leave(now: LocalDateTime) {
        if (!isCurrent()) {
            throw BusinessException(WorkplaceErrorCode.MEMBERSHIP_ALREADY_ENDED)
        }
        if (role != WorkplaceRole.EMPLOYEE) {
            throw BusinessException(WorkplaceErrorCode.MANAGER_CANNOT_LEAVE)
        }

        leftAt = now
    }
}
