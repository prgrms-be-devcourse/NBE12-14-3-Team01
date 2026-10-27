package com.merge.backend.global.exception

import org.springframework.http.HttpStatus

enum class GlobalErrorCode(
    private val httpStatus: HttpStatus,
    private val code: String,
    private val message: String,
) : ErrorCode {

    VALIDATION_ERROR(
        HttpStatus.BAD_REQUEST,
        "COM-001",
        "요청 값이 올바르지 않습니다.",
    ),

    INVALID_REQUEST_BODY(
        HttpStatus.BAD_REQUEST,
        "COM-002",
        "요청 형식이 올바르지 않습니다.",
    ),

    INVALID_REQUEST_PARAMETER(
        HttpStatus.BAD_REQUEST,
        "COM-003",
        "요청 파라미터가 올바르지 않습니다.",
    ),

    INTERNAL_SERVER_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "COM-500",
        "서버 내부 오류가 발생했습니다.",
    ),
    ;

    override fun getHttpStatus(): HttpStatus = httpStatus

    override fun getCode(): String = code

    override fun getMessage(): String = message
}
