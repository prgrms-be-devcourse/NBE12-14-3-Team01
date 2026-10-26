package com.merge.backend.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    @DisplayName("ErrorCode만 넘기면 메시지는 ErrorCode의 메시지이고 data는 null이다")
    void createWithErrorCode() {
        BusinessException exception =
            new BusinessException(GlobalErrorCode.INVALID_REQUEST_PARAMETER);

        assertThat(exception.getErrorCode()).isSameAs(GlobalErrorCode.INVALID_REQUEST_PARAMETER);
        assertThat(exception.getMessage()).isEqualTo("요청 파라미터가 올바르지 않습니다.");
        assertThat(exception.getData()).isNull();
    }

    @Test
    @DisplayName("data를 함께 넘기면 그대로 보관한다")
    void createWithData() {
        Map<String, Boolean> data = Map.of("requiresConfirmation", true);

        BusinessException exception =
            new BusinessException(GlobalErrorCode.VALIDATION_ERROR, data);

        assertThat(exception.getErrorCode()).isSameAs(GlobalErrorCode.VALIDATION_ERROR);
        assertThat(exception.getMessage()).isEqualTo("요청 값이 올바르지 않습니다.");
        assertThat(exception.getData()).isSameAs(data);
    }

    @Test
    @DisplayName("Unchecked 예외라서 throws 선언 없이 던질 수 있다")
    void isRuntimeException() {
        assertThat(new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR))
            .isInstanceOf(RuntimeException.class);
    }
}
