package com.merge.backend.domain.substitute.entity

import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.global.entity.BaseEntity
import com.merge.backend.global.exception.BusinessException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

@Entity
class SubstituteRequest(
    shift: Shift,
    requesterMember: WorkplaceMember,
    status: RequestStatus,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    var shift: Shift = shift
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_member_id", nullable = false)
    var requesterMember: WorkplaceMember = requesterMember
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RequestStatus = status
        protected set

    @Enumerated(EnumType.STRING)
    var closeReason: RequestCloseReason? = null
        protected set

    var approvedAt: LocalDateTime? = null
        protected set

    var closedAt: LocalDateTime? = null
        protected set

    // Request가 아직 OPEN 상태인지 확인
    fun validateOpening() {
        if (status != RequestStatus.OPEN) {
            throw BusinessException(SubstituteRequestErrorCode.VALIDATE_STATUS_CLOSED)
        }
    }

    // Candidate가 대타 요청을 수락하면 Request도 ACCEPTED로 변경
    fun accept() {
        validateOpening()

        status = RequestStatus.ACCEPTED
    }

    // 모든 Candidate가 거절한 경우 Request 종료
    fun closeAllCandidatesRejected(now: LocalDateTime) {
        status = RequestStatus.CLOSED
        closeReason = RequestCloseReason.ALL_CANDIDATES_REJECTED
        closedAt = now
    }

    // MANAGER가 최종 승인하면 실제 Shift 담당자 변경
    fun approveRequest(acceptedMember: WorkplaceMember, now: LocalDateTime) {
        // Shift 담당자를 대타 근무자로 변경
        shift.changeMember(acceptedMember)

        // Request 최종 상태 및 승인/마감 시각 변경
        status = RequestStatus.APPROVED
        approvedAt = now
        closedAt = now
        closeReason = null
    }

    // 요청 종료
    fun closeByManager(now: LocalDateTime) {
        status = RequestStatus.CLOSED
        closeReason = RequestCloseReason.MANAGER_CLOSED
        closedAt = now
        approvedAt = null
    }

    // 근무가 취소·인계되어 요청 종료 (후보 상태와 수락 기록은 그대로)
    fun closeByShiftChange(now: LocalDateTime) {
        status = RequestStatus.CLOSED
        closeReason = RequestCloseReason.SHIFT_CHANGED
        closedAt = now
    }
}
