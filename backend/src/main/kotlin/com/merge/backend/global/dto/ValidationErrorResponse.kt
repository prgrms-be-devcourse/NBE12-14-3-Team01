package com.merge.backend.global.dto

@JvmRecord
data class ValidationErrorResponse(
    val fieldErrors: List<FieldErrorDetail>,
) {

    // Validator가 기본 메시지를 주지 않으면 message는 null일 수 있다 (FieldError.getDefaultMessage)
    @JvmRecord
    data class FieldErrorDetail(
        val field: String,
        val message: String?,
    )
}
