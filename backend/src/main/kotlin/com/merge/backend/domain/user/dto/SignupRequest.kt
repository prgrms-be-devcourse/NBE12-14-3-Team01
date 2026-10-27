package com.merge.backend.domain.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

// 필드가 빠져도 COM-001(필드 오류)로 응답하도록 null 가능으로 둠
data class SignupRequest(
    @field:NotBlank @field:Email
    val email: String?,
    @field:NotBlank
    val password: String?,
    @field:NotBlank
    val name: String?,
)
