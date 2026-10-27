package com.merge.backend.domain.shift.dto

import jakarta.validation.constraints.NotNull
import kotlin.jvm.JvmRecord

@JvmRecord
data class SchedulePublishRequest(
    @field:NotNull(message = "근무 불가능 일정 충돌 확인 여부는 필수입니다.")
    val confirmUnavailableConflict: Boolean?
)
