package com.merge.backend.domain.user.service

import com.jayway.jsonpath.JsonPath
import com.merge.backend.domain.user.entity.User
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class AuthTokenServiceTest {

    // 비밀키 길이에 따라 서명 방식이 정해짐 (64바이트 이상이면 서명이 가장 긴 HS512)
    private fun createAuthTokenService(secretLength: Int): AuthTokenService {
        val authTokenService = AuthTokenService()
        ReflectionTestUtils.setField(authTokenService, "secretPattern", "s".repeat(secretLength))
        ReflectionTestUtils.setField(authTokenService, "refreshExpireMills", REFRESH_EXPIRE_MILLS)
        return authTokenService
    }

    // 토큰의 header(0번)나 payload(1번)를 JSON 문자열로 꺼냄
    private fun decodePart(token: String, index: Int): String =
        String(Base64.getUrlDecoder().decode(token.split(".")[index]))

    private fun issuedAt(token: String): Long {
        val issuedAt: Number = JsonPath.read(decodePart(token, 1), "$.iat")
        return issuedAt.toLong()
    }

    @Test
    @DisplayName("같은 사용자가 같은 1초 안에 Refresh Token을 두 번 받아도 서로 다르다")
    fun genRefreshTokenReturnsDifferentTokensInSameSecond() {
        // given
        val authTokenService = createAuthTokenService(40)
        val user = User(1L, "user@test.com", "사용자")

        // when: 같은 1초 안에 두 번 발급 (그 사이 초가 바뀌면 다시)
        var first: String
        var second: String
        do {
            first = authTokenService.genRefreshToken(user)
            second = authTokenService.genRefreshToken(user)
        } while (issuedAt(first) != issuedAt(second))

        // then
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    @DisplayName("서명이 가장 긴 비밀키로 7자리 id에 발급해도 Refresh Token은 255자 이하다")
    fun genRefreshTokenFitsRefreshTokenColumn() {
        // given: 이 비밀키에서 id가 8자리(1,000만) 이상이면 255자를 넘음
        val authTokenService = createAuthTokenService(64)
        val user = User(9_999_999L, "user@test.com", "사용자")

        // when
        val refreshToken = authTokenService.genRefreshToken(user)

        // then: HS512로 서명됐는지 먼저 확인하고 users.refresh_token 길이(255)와 비교
        val algorithm: String = JsonPath.read(decodePart(refreshToken, 0), "$.alg")
        assertThat(algorithm).isEqualTo("HS512")
        assertThat(refreshToken.length).isLessThanOrEqualTo(255)
    }

    companion object {
        private const val REFRESH_EXPIRE_MILLS = 1000L * 60 * 60 * 24 * 14
    }
}
