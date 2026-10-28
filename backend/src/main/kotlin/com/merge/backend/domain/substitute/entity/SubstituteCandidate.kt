package com.merge.backend.domain.substitute.entity

import com.merge.backend.domain.workplace.entity.WorkplaceMember
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
        UniqueConstraint(columnNames = ["request_id", "member_id"]),
    ],
)
class SubstituteCandidate(
    request: SubstituteRequest,
    member: WorkplaceMember,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    var request: SubstituteRequest = request
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: WorkplaceMember = member
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CandidateStatus = CandidateStatus.PENDING
        protected set

    var respondedAt: LocalDateTime? = null
        protected set

    fun reject(now: LocalDateTime) {
        status = CandidateStatus.REJECTED // 대타 요청을 거절한 상태
        respondedAt = now // 거절한 시간
    }

    // 후보가 퇴사해 응답할 수 없게 됨. 본인 응답이 아니므로 respondedAt은 그대로 null
    fun markIneligible() {
        check(status == CandidateStatus.PENDING) { "응답하지 않은 후보만 INELIGIBLE로 바꿀 수 있습니다." }
        status = CandidateStatus.INELIGIBLE
    }

    // 1. 후보자 중 1명이 수락을 눌렀을 때
    fun acceptRequest(now: LocalDateTime) {
        // 본인 상태 업데이트
        status = CandidateStatus.ACCEPTED
        respondedAt = now

        // 연관된 대체근무 요청 상태 승인으로 변경
        request.accept()
    }
}
