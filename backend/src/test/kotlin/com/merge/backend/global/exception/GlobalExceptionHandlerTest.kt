package com.merge.backend.global.exception

import com.merge.backend.global.dto.ConfirmationRequiredResponse
import com.merge.backend.global.dto.ValidationErrorResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun BusinessException을_ErrorCode에_맞는_응답으로_변환한다() {
        // given
        val exception = BusinessException(
            GlobalErrorCode.INVALID_REQUEST_BODY
        )

        // when
        val response = handler.handleBusinessException(exception)

        // then
        assertThat(response.statusCode)
            .isEqualTo(HttpStatus.BAD_REQUEST)

        val body = response.body

        assertThat(body).isNotNull()
        assertThat(body!!.success).isFalse()
        assertThat(body.code).isEqualTo("COM-002")
        assertThat(body.message)
            .isEqualTo("요청 형식이 올바르지 않습니다.")
        assertThat(body.data).isNull()
    }

    @Test
    fun BusinessException의_data를_응답_data로_전달한다() {
        // given
        val data = ConfirmationRequiredResponse(true)

        val exception = BusinessException(
            GlobalErrorCode.INVALID_REQUEST_BODY,
            data,
        )

        // when
        val response = handler.handleBusinessException(exception)

        // then
        assertThat(response.statusCode)
            .isEqualTo(HttpStatus.BAD_REQUEST)

        val body = response.body

        assertThat(body).isNotNull()
        assertThat(body!!.success).isFalse()
        assertThat(body.code)
            .isEqualTo("COM-002")
        assertThat(body.data)
            .isEqualTo(data)
    }

    @Test
    fun 읽을_수_없는_RequestBody를_잘못된_요청_응답으로_변환한다() {
        // given
        val exception = HttpMessageNotReadableException(
            "JSON parse error",
            MockHttpInputMessage(ByteArray(0)),
        )

        // when
        val response = handler.handleHttpMessageNotReadableException(exception)

        // then
        assertThat(response.statusCode)
            .isEqualTo(HttpStatus.BAD_REQUEST)

        val body = response.body

        assertThat(body).isNotNull()
        assertThat(body!!.success).isFalse()
        assertThat(body.code).isEqualTo("COM-002")
        assertThat(body.message)
            .isEqualTo("요청 형식이 올바르지 않습니다.")
        assertThat(body.data).isNull()
    }

    @Test
    fun 예상하지_못한_예외를_서버_내부_오류_응답으로_변환한다() {
        // given
        val exception: Exception = RuntimeException("내부 상세 오류")

        // when
        val response = handler.handleException(exception)

        // then
        assertThat(response.statusCode)
            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)

        val body = response.body

        assertThat(body).isNotNull()
        assertThat(body!!.success).isFalse()
        assertThat(body.code).isEqualTo("COM-500")
        assertThat(body.message)
            .isEqualTo("서버 내부 오류가 발생했습니다.")
        assertThat(body.data).isNull()
    }

    @Test
    fun 여러_Validation_오류를_필드별로_구조화해서_반환한다() {
        // given
        val bindingResult = BeanPropertyBindingResult(
            Any(),
            "request",
        )

        bindingResult.addError(
            FieldError(
                "request",
                "email",
                "이메일을 입력해주세요.",
            )
        )

        bindingResult.addError(
            FieldError(
                "request",
                "password",
                "비밀번호는 8자 이상이어야 합니다.",
            )
        )

        val method = GlobalExceptionHandlerTest::class.java
            .getDeclaredMethod(
                "dummyControllerMethod",
                Any::class.java,
            )

        val methodParameter = MethodParameter(method, 0)

        val exception = MethodArgumentNotValidException(
            methodParameter,
            bindingResult,
        )

        // when
        val response = handler.handleMethodArgumentNotValidException(exception)

        // then
        assertThat(response.statusCode)
            .isEqualTo(HttpStatus.BAD_REQUEST)

        val body = response.body

        assertThat(body).isNotNull()
        assertThat(body!!.success).isFalse()
        assertThat(body.code).isEqualTo("COM-001")
        assertThat(body.message)
            .isEqualTo("요청 값이 올바르지 않습니다.")

        val data = body.data

        assertThat(data).isNotNull()

        assertThat(data.fieldErrors)
            .containsExactlyInAnyOrder(
                ValidationErrorResponse.FieldErrorDetail(
                    "email",
                    "이메일을 입력해주세요.",
                ),
                ValidationErrorResponse.FieldErrorDetail(
                    "password",
                    "비밀번호는 8자 이상이어야 합니다.",
                ),
            )
    }

    @Test
    fun 요청_파라미터_타입_변환_실패를_잘못된_요청_응답으로_변환한다() {
        // given
        val method = GlobalExceptionHandlerTest::class.java
            .getDeclaredMethod(
                "dummyLongParameterMethod",
                Long::class.javaObjectType,
            )

        val methodParameter = MethodParameter(method, 0)

        val exception = MethodArgumentTypeMismatchException(
            "abc",
            Long::class.javaObjectType,
            "shiftId",
            methodParameter,
            NumberFormatException("For input string: \"abc\""),
        )

        // when
        val response = handler.handleMethodArgumentTypeMismatchException(exception)

        // then
        assertThat(response.statusCode)
            .isEqualTo(HttpStatus.BAD_REQUEST)

        val body = response.body

        assertThat(body).isNotNull()
        assertThat(body!!.success).isFalse()
        assertThat(body.code).isEqualTo("COM-003")
        assertThat(body.message)
            .isEqualTo("요청 파라미터가 올바르지 않습니다.")
        assertThat(body.data).isNull()
    }

    @Test
    fun 필수_요청_파라미터_누락을_잘못된_요청_응답으로_변환한다() {
        // given
        val exception = MissingServletRequestParameterException(
            "weekStartDate",
            "LocalDate",
        )

        // when
        val response = handler.handleMissingServletRequestParameterException(exception)

        // then
        assertThat(response.statusCode)
            .isEqualTo(HttpStatus.BAD_REQUEST)

        val body = response.body

        assertThat(body).isNotNull()
        assertThat(body!!.success).isFalse()
        assertThat(body.code).isEqualTo("COM-003")
        assertThat(body.message)
            .isEqualTo("요청 파라미터가 올바르지 않습니다.")
        assertThat(body.data).isNull()
    }

    // MethodParameter를 만들기 위해 Reflection으로만 참조하는 메서드
    @Suppress("unused", "UNUSED_PARAMETER")
    private fun dummyControllerMethod(request: Any) {
    }

    // Long?는 JVM에서 java.lang.Long 파라미터가 된다 (Java Test의 Long.class와 같음)
    @Suppress("unused", "UNUSED_PARAMETER")
    private fun dummyLongParameterMethod(shiftId: Long?) {
    }
}
