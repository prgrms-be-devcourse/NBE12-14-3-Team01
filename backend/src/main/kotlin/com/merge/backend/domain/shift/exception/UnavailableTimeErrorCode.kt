package com.merge.backend.domain.shift.exception

import com.merge.backend.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class UnavailableTimeErrorCode(
    private val httpStatus: HttpStatus,
    private val code: String,
    private val message: String
) : ErrorCode {
    INVALID_TIME_RANGE(
        HttpStatus.BAD_REQUEST,
        "UNA-001",
        "시작 시간은 종료 시간보다 빨라야 합니다."
    ),
    NOT_FUTURE_TIME(
        HttpStatus.BAD_REQUEST,
        "UNA-002",
        "근무 불가능 일정은 미래 시간만 등록할 수 있습니다."
    ),
    TIME_OVERLAP(
        HttpStatus.CONFLICT,
        "UNA-003",
        "기존 근무 불가능 일정과 시간이 겹칩니다."
    ),
    OFFICIAL_SHIFT_CONFLICT(
        HttpStatus.BAD_REQUEST,
        "OFFICIAL_SHIFT_CONFLICT",
        "이미 확정된 근무와 겹치는 시간입니다."
    ),
    NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "UNA-404",
        "근무 불가능 일정을 찾을 수 없습니다."
    ),
    FORBIDDEN(
        HttpStatus.FORBIDDEN,
        "UNA-403",
        "본인의 근무 불가능 일정만 수정할 수 있습니다."
    ),
    NOT_MODIFIABLE_TIME(
        HttpStatus.BAD_REQUEST,
        "UNA-004",
        "이미 시작되었거나 지난 일정은 수정할 수 없습니다."
    );

    override fun getHttpStatus(): HttpStatus = httpStatus

    override fun getCode(): String = code

    override fun getMessage(): String = message
}
