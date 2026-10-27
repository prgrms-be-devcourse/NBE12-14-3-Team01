package com.merge.backend.global.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

// Jackson 3은 기본으로 필드를 알파벳 순으로 정렬하므로 Contract(CB-COM-02) 순서를 명시한다
@JsonPropertyOrder("success", "code", "message", "data")
class ApiResponse<T> private constructor(
    // Java 호출부가 Lombok boolean getter 이름(isSuccess)을 쓰므로 맞추고,
    // 그러면 Kotlin Jackson Module이 JSON 이름을 "isSuccess"로 내보내므로 이름을 고정한다
    @get:JvmName("isSuccess")
    @get:JsonProperty("success")
    val success: Boolean,
    val code: String,
    val message: String,
    // T?로 두면 springdoc이 모든 data Schema에 null 허용을 추가하므로 T로 둔다 (TS-07)
    val data: T,
) {

    companion object {

        // 기본 성공 응답(code + message + data)
        @JvmStatic
        fun <T> success(code: String, message: String, data: T): ApiResponse<T> =
            ApiResponse(true, code, message, data)

        // 성공 응답(메시지가 없을 때)
        @JvmStatic
        fun <T> success(code: String, data: T): ApiResponse<T> = success(code, "", data)

        @JvmStatic
        fun success(code: String, message: String): ApiResponse<Void?> =
            success<Void?>(code, message, null)

        // 에러(데이터를 같이 보낼 때)
        @JvmStatic
        fun <T> error(code: String, message: String, data: T): ApiResponse<T> =
            ApiResponse(false, code, message, data)

        // 데이터가 없는 에러
        @JvmStatic
        fun error(code: String, message: String): ApiResponse<Void?> =
            error<Void?>(code, message, null)
    }
}
