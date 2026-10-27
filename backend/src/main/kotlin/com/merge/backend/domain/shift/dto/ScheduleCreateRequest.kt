package com.merge.backend.domain.shift.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import kotlin.jvm.JvmRecord

@JvmRecord
data class ScheduleCreateRequest(
    @field:NotNull(message = "주 시작일은 필수입니다.")
    val weekStartDate: LocalDate?
)
