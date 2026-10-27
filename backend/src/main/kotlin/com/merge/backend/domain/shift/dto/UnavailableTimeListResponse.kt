package com.merge.backend.domain.shift.dto

import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class UnavailableTimeListResponse(
    val unavailableTimeId: Long?,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
    val officialShiftConflict: Boolean
)
