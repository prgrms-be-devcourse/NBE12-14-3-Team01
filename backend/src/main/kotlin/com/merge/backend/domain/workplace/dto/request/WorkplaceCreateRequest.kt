package com.merge.backend.domain.workplace.dto.request

import jakarta.validation.constraints.NotBlank
import kotlin.jvm.JvmRecord

@JvmRecord
data class WorkplaceCreateRequest(
    @field:NotBlank val name: String?
)