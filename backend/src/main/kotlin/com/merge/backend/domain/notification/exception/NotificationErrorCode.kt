package com.merge.backend.domain.notification.exception

import com.merge.backend.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class NotificationErrorCode(
    private val status: HttpStatus,
    private val errorCode: String,
    private val errorMessage: String
) : ErrorCode {
    NOT_FOUND_NOTIFICATION(
        HttpStatus.NOT_FOUND,
        "NOT-001",
        "알림을 찾을 수 없습니다."
    ),
    FORBIDDEN_ACCESS(
        HttpStatus.FORBIDDEN,
        "NOT-002",
        "해당 알림에 접근할 권한이 없습니다."
    );

    override fun getHttpStatus(): HttpStatus = status

    override fun getCode(): String = errorCode

    override fun getMessage(): String = errorMessage
}
