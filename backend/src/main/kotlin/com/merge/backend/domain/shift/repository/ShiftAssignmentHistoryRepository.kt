package com.merge.backend.domain.shift.repository

import com.merge.backend.domain.shift.entity.ShiftAssignmentHistory
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ShiftAssignmentHistoryRepository : JpaRepository<ShiftAssignmentHistory, Long> {

    fun findAllByShift_IdOrderByCreateDateAsc(shiftId: Long): List<ShiftAssignmentHistory>

    fun findAllByPreviousMember_IdOrderByCreateDateDescIdDesc(memberId: Long): List<ShiftAssignmentHistory>

    // API-SC-01 - 나의 현재 소속이 이전 담당인 이력 중 근무별 마지막 Row.
    // 지금 다시 내가 담당인 근무는 제외 (A -> C -> A)
    @Query(
        """
        SELECT h
        FROM ShiftAssignmentHistory h
        JOIN FETCH h.shift s
        JOIN FETCH s.schedule sc
        JOIN FETCH sc.workplace
        JOIN h.previousMember pm
        WHERE pm.user.id = :userId
          AND pm.leftAt IS NULL
          AND s.startAt >= :from
          AND s.member.id <> pm.id
          AND h.id = (
              SELECT MAX(h2.id)
              FROM ShiftAssignmentHistory h2
              WHERE h2.shift.id = s.id
                AND h2.previousMember.id = pm.id
          )
        """
    )
    fun findLatestReassignedAwayOfCurrentMembers(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDateTime,
    ): List<ShiftAssignmentHistory>
}
