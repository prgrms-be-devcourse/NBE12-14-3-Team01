package com.merge.backend.global.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;


// Jackson 3은 기본으로 필드를 알파벳 순으로 정렬하므로 Contract(CB-COM-02) 순서를 명시한다
@JsonPropertyOrder({"success", "code", "message", "data"})
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;

    //기본 성공 응답(code + message + data)
    public static <T> ApiResponse<T> success(
        String code,
        String message,
        T data
    ) {
        return ApiResponse.<T>builder()
            .success(true)
            .code(code)
            .message(message)
            .data(data)
            .build();
    }

    //성공 응답(메시지가 없을 때)
    public static <T> ApiResponse<T> success(
        String code,
        T data
    ) {
        return success(code, "", data);
    }

    public static ApiResponse<Void> success(
        String code,
        String message
    ) {
        return ApiResponse.<Void>success(code, message, null);
    }

    //에러(데이터를 같이 보낼 때)
    public static <T> ApiResponse<T> error(
        String code,
        String message,
        T data
    ) {
        return ApiResponse.<T>builder()
            .success(false)
            .code(code)
            .message(message)
            .data(data)
            .build();
    }

    //데이터가 없는 에러
    public static ApiResponse<Void> error(
        String code,
        String message
    ) {
        return ApiResponse.<Void>error(code, message, null);
    }
}