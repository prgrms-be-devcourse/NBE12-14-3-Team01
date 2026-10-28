package com.merge.backend.global.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiResponseTest {

    @Test
    fun 데이터가_있는_성공_응답을_생성한다() {
        // given
        val data = "test-data"

        // when
        val response: ApiResponse<String> =
            ApiResponse.success(
                "TEST-001",
                "성공했습니다.",
                data,
            )

        // then
        assertThat(response.success).isTrue()
        assertThat(response.code).isEqualTo("TEST-001")
        assertThat(response.message).isEqualTo("성공했습니다.")
        assertThat(response.data).isEqualTo(data)
    }

    @Test
    fun 데이터가_없는_성공_응답을_생성한다() {
        // when
        val response: ApiResponse<Void?> =
            ApiResponse.success(
                "TEST-001",
                "성공했습니다.",
            )

        // then
        assertThat(response.success).isTrue()
        assertThat(response.code).isEqualTo("TEST-001")
        assertThat(response.message).isEqualTo("성공했습니다.")
        assertThat(response.data).isNull()
    }

    @Test
    fun 데이터가_있는_에러_응답을_생성한다() {
        // given
        val data = "error-data"

        // when
        val response: ApiResponse<String> =
            ApiResponse.error(
                "TEST-ERROR",
                "실패했습니다.",
                data,
            )

        // then
        assertThat(response.success).isFalse()
        assertThat(response.code).isEqualTo("TEST-ERROR")
        assertThat(response.message).isEqualTo("실패했습니다.")
        assertThat(response.data).isEqualTo(data)
    }

    @Test
    fun 데이터가_없는_에러_응답을_생성한다() {
        // when
        val response: ApiResponse<Void?> =
            ApiResponse.error(
                "TEST-ERROR",
                "실패했습니다.",
            )

        // then
        assertThat(response.success).isFalse()
        assertThat(response.code).isEqualTo("TEST-ERROR")
        assertThat(response.message).isEqualTo("실패했습니다.")
        assertThat(response.data).isNull()
    }
}
