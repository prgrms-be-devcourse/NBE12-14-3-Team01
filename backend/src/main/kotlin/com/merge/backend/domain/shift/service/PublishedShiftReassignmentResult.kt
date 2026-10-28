package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.entity.Shift

data class PublishedShiftReassignmentResult(
    val shift: Shift,
    val closedSubstituteRequests: Int,
)
