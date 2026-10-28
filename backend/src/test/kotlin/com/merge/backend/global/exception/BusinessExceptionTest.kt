package com.merge.backend.global.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BusinessExceptionTest {

    @Test
    @DisplayName("ErrorCode만 넘기면 메시지는 ErrorCode의 메시지이고 data는 null이다")
    fun createWithErrorCode() {
        val exception = BusinessException(GlobalErrorCode.INVALID_REQUEST_PARAMETER)

        assertThat(exception.errorCode).isSameAs(GlobalErrorCode.INVALID_REQUEST_PARAMETER)
        assertThat(exception.message).isEqualTo("요청 파라미터가 올바르지 않습니다.")
        assertThat(exception.data).isNull()
    }

    @Test
    @DisplayName("data를 함께 넘기면 그대로 보관한다")
    fun createWithData() {
        val data = mapOf("requiresConfirmation" to true)

        val exception = BusinessException(GlobalErrorCode.VALIDATION_ERROR, data)

        assertThat(exception.errorCode).isSameAs(GlobalErrorCode.VALIDATION_ERROR)
        assertThat(exception.message).isEqualTo("요청 값이 올바르지 않습니다.")
        assertThat(exception.data).isSameAs(data)
    }

    @Test
    @DisplayName("Unchecked 예외라서 throws 선언 없이 던질 수 있다")
    fun isRuntimeException() {
        assertThat(BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR))
            .isInstanceOf(RuntimeException::class.java)
    }
}
