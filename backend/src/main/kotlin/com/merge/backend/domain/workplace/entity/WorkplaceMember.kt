package com.merge.backend.domain.workplace.entity

import com.merge.backend.domain.user.entity.User
import com.merge.backend.global.entity.BaseEntity
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
     * check는 내부 상태 안전장치다. Command는 이미 종료된 소속·관리자 대상 요청을
     * 호출 전에 업무 오류로 처리해야 한다. 해당 ErrorCode는 아직 구현되지 않았다.
     */
    fun leave(now: LocalDateTime) {
        check(isCurrent()) { "이미 종료된 소속입니다." }
        check(role == WorkplaceRole.EMPLOYEE) { "관리자 소속은 퇴사 처리할 수 없습니다." }

        leftAt = now
    }
}
