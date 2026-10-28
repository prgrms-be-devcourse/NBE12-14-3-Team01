package com.merge.backend.domain.shift.dto

import com.merge.backend.global.dto.UnavailableConflictOverlap
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class PublishedShiftConfirmationRequiredResponse(
    val requiresConfirmation: Boolean,
    val unavailableConflicts: List<NewShiftUnavailableConflictResponse>,
)

@JvmRecord
data class NewShiftUnavailableConflictResponse(
    val newShiftKey: NewShiftKey,
    val targetName: String?,
    val overlaps: List<UnavailableConflictOverlap>,
)
