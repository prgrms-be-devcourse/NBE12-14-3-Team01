package com.merge.backend.global.exception

import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.dto.ValidationErrorResponse
import com.merge.backend.global.dto.ValidationErrorResponse.FieldErrorDetail
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Any?>> {
        val errorCode = e.errorCode

        log.warn("BusinessException: [{}] {}", errorCode.getCode(), errorCode.getMessage())

        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), e.data))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        e: HttpMessageNotReadableException,
    ): ResponseEntity<ApiResponse<Void?>> {
        val errorCode = GlobalErrorCode.INVALID_REQUEST_BODY

        log.warn("HttpMessageNotReadableException: [{}] {}", errorCode.getCode(), e.message)

        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        e: MethodArgumentNotValidException,
    ): ResponseEntity<ApiResponse<ValidationErrorResponse>> {
        val errorCode = GlobalErrorCode.VALIDATION_ERROR

        val fieldErrors = e.bindingResult.fieldErrors
            .map { error -> FieldErrorDetail(error.field, error.defaultMessage) }

        val data = ValidationErrorResponse(fieldErrors)

        log.warn(
            "MethodArgumentNotValidException: [{}] fieldErrors={}",
            errorCode.getCode(),
            fieldErrors,
        )

        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), data))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        e: MethodArgumentTypeMismatchException,
    ): ResponseEntity<ApiResponse<Void?>> {
        val errorCode = GlobalErrorCode.INVALID_REQUEST_PARAMETER

        log.warn(
            "MethodArgumentTypeMismatchException: [{}] parameter={}",
            errorCode.getCode(),
            e.name,
        )

        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(
        e: MissingServletRequestParameterException,
    ): ResponseEntity<ApiResponse<Void?>> {
        val errorCode = GlobalErrorCode.INVALID_REQUEST_PARAMETER

        log.warn(
            "MissingServletRequestParameterException: [{}] parameter={}",
            errorCode.getCode(),
            e.parameterName,
        )

        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Void?>> {
        val errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR

        log.error("Unhandled Exception 발생", e)

        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()))
    }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
