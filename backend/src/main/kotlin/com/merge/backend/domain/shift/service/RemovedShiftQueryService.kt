package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.dto.RemovedShiftResponse
import com.merge.backend.domain.shift.repository.ShiftAssignmentHistoryRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RemovedShiftQueryService(
    private val shiftRepository: ShiftRepository,
    private val shiftAssignmentHistoryRepository: ShiftAssignmentHistoryRepository,
    private val clock: Clock,
) {

    // API-SC-01 - 빠진 근무. 상태 변경 없음
    @Transactional(readOnly = true)
    fun findRemovedShifts(
        userId: Long, // 현재 로그인한 User의 ID
    ): List<RemovedShiftResponse> {
        // 원래 근무 시작 시각 기준 미래 + 최근 7일 (조회 범위일 뿐 보존 기간이 아님)
        val from = LocalDateTime.now(clock).minusDays(7)

        // ① 취소된 근무는 끝 상태라 shift.member가 취소 당시 담당 / ② 넘어간 근무는 배정 이력에서 찾음.
        // ①은 지금 내 근무, ②는 지금 내 근무가 아닌 것이라 같은 근무가 두 번 나오지 않는다
        val cancelled = shiftRepository.findCancelledShiftsOfCurrentMembers(userId, from)
            .map { RemovedShiftResponse.fromCancelled(it) }
        val reassigned = shiftAssignmentHistoryRepository.findLatestReassignedAwayOfCurrentMembers(userId, from)
            .map { RemovedShiftResponse.fromReassigned(it) }

        // 정렬은 API-SB-01과 같이 사건 시각 최신순, 같으면 근무 ID 최신순
        return (cancelled + reassigned).sortedWith(
            compareByDescending<RemovedShiftResponse> { it.occurredAt }
                .thenByDescending { it.shiftId },
        )
    }
}
