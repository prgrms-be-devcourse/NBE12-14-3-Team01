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

    @Test
    @DisplayName("쿠키를 지울 때 Path, HttpOnly, Secure, SameSite, Domain이 발급할 때와 같다")
    fun deleteCookieUsesSameAttributesAsIssuedCookie() {
        // given: 배포처럼 속성을 모두 채운 설정
        val rq = createRq("example.com", true, "None")

        // when: 같은 설정으로 발급하고 삭제
        rq.addCookie("refreshToken", "token")
        rq.deleteCookie("refreshToken")

        // then: 속성이 하나라도 다르면 브라우저가 다른 쿠키로 보고 지우지 않음
        val cookies = response.cookies.filter { cookie -> cookie.name == "refreshToken" }
        assertThat(cookies).hasSize(2)
        val issued = cookies[0]
        val deleted = cookies[1]

        // 빈 값끼리 같아서 통과하지 않도록 발급 쿠키에 설정값이 들어갔는지 먼저 확인
        assertThat(issued.path).isEqualTo("/")
        assertThat(issued.isHttpOnly).isTrue()
        assertThat(issued.secure).isTrue()
        assertThat(issued.getAttribute("SameSite")).isEqualTo("None")
        assertThat(issued.domain).isEqualTo("example.com")

        assertThat(deleted.maxAge).isZero()
        assertThat(deleted.path).isEqualTo(issued.path)
        assertThat(deleted.isHttpOnly).isEqualTo(issued.isHttpOnly)
        assertThat(deleted.secure).isEqualTo(issued.secure)
        assertThat(deleted.getAttribute("SameSite")).isEqualTo(issued.getAttribute("SameSite"))
        assertThat(deleted.domain).isEqualTo(issued.domain)
    }
}
