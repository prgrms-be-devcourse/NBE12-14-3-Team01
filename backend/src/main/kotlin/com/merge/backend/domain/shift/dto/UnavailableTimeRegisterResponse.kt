package com.merge.backend.domain.shift.dto

import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class UnavailableTimeRegisterResponse(
    val unavailableTimeId: Long?,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?
)
