package com.merge.backend.domain.shift.repository

import com.merge.backend.domain.shift.entity.ShiftAssignmentHistory
import org.springframework.data.jpa.repository.JpaRepository

interface ShiftAssignmentHistoryRepository : JpaRepository<ShiftAssignmentHistory, Long> {

    fun findAllByShift_IdOrderByCreateDateAsc(shiftId: Long): List<ShiftAssignmentHistory>

    fun findAllByPreviousMember_IdOrderByCreateDateDesc(memberId: Long): List<ShiftAssignmentHistory>
}
