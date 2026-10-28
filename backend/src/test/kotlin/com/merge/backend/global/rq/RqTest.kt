package com.merge.backend.global.rq

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.util.ReflectionTestUtils

class RqTest {

    private val response = MockHttpServletResponse()

    // 쿠키 설정값을 넣어서 Rq 만들기
    private fun createRq(domain: String?, secure: Boolean, sameSite: String?): Rq {
        val rq = Rq(MockHttpServletRequest(), response)
        ReflectionTestUtils.setField(rq, "cookieDomain", domain)
        ReflectionTestUtils.setField(rq, "cookieSecure", secure)
        ReflectionTestUtils.setField(rq, "cookieSameSite", sameSite)
        return rq
    }

    @Test
    @DisplayName("로컬 기본값이면 도메인 없이 Secure와 SameSite도 안 붙인다")
    fun test1() {
        val rq = createRq("", false, "")

        rq.addCookie("refreshToken", "token")

        val cookie = requireNotNull(response.getCookie("refreshToken"))
        assertThat(cookie.domain).isNull()
        assertThat(cookie.secure).isFalse()
        assertThat(cookie.getAttribute("SameSite")).isNull()
        assertThat(cookie.isHttpOnly).isTrue()
        assertThat(cookie.path).isEqualTo("/")
    }

    @Test
    @DisplayName("배포값이면 도메인 없이 Secure와 SameSite를 붙인다")
    fun test2() {
        val rq = createRq("", true, "Lax")

        rq.addCookie("refreshToken", "token")

        val cookie = requireNotNull(response.getCookie("refreshToken"))
        assertThat(cookie.domain).isNull()
        assertThat(cookie.secure).isTrue()
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax")
        assertThat(cookie.isHttpOnly).isTrue()
    }

    @Test
    @DisplayName("도메인을 넣으면 그 도메인으로 쿠키를 만든다")
    fun test3() {
        val rq = createRq("localhost", false, "")

        rq.addCookie("refreshToken", "token")

        assertThat(requireNotNull(response.getCookie("refreshToken")).domain).isEqualTo("localhost")
    }

    @Test
    @DisplayName("설정값이 null이어도 쿠키를 만든다")
    fun test4() {
        val rq = createRq(null, false, null)

        rq.addCookie("refreshToken", "token")

        val cookie = requireNotNull(response.getCookie("refreshToken"))
        assertThat(cookie.domain).isNull()
        assertThat(cookie.getAttribute("SameSite")).isNull()
    }

    @Test
    @DisplayName("쿠키를 지울 때도 같은 속성으로 만든다")
    fun test5() {
        val rq = createRq("", true, "Lax")

        rq.deleteCookie("refreshToken")

        val cookie = requireNotNull(response.getCookie("refreshToken"))
        assertThat(cookie.maxAge).isZero()
        assertThat(cookie.domain).isNull()
        assertThat(cookie.secure).isTrue()
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax")
        assertThat(cookie.isHttpOnly).isTrue()
    }
}
