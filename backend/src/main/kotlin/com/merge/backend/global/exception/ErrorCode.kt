package com.merge.backend.global.exception

import org.springframework.http.HttpStatus

// Java enum(Lombok @Getter)과 Kotlin enum이 함께 구현하므로 getter 이름의 함수로 둔다 (CB-COM-03)
interface ErrorCode {

    fun getHttpStatus(): HttpStatus

    fun getCode(): String

    fun getMessage(): String
}
