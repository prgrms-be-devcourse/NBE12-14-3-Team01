package com.merge.backend.global.rq

import com.merge.backend.global.security.SecurityUser
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class Rq(
    private val request: HttpServletRequest,
    private val response: HttpServletResponse,
) {

    @Value("\${custom.cookie.domain}")
    private var cookieDomain: String? = null

    @Value("\${custom.cookie.secure}")
    private var cookieSecure: Boolean = false

    @Value("\${custom.cookie.same-site}")
    private var cookieSameSite: String? = null

    // Kotlin에서는 rq.actorId, Java에서는 rq.getActorId()로 부름
    val actorId: Long?
        get() {
            val authentication = SecurityContextHolder.getContext().authentication
            val principal = authentication?.principal as SecurityUser
            return principal.id
        }

    fun getCookieValue(name: String?, defaultValue: String?): String? {
        return request.cookies
            ?.filter { cookie -> cookie.name == name }
            ?.map { cookie -> cookie.value }
            ?.firstOrNull { value -> !value.isBlank() }
            ?: defaultValue
    }

    fun addCookie(name: String?, value: String?) {
        val cookie = Cookie(name, value)
        applyCookieSettings(cookie)

        response.addCookie(cookie)
    }

    fun deleteCookie(name: String?) {
        val cookie = Cookie(name, "")
        applyCookieSettings(cookie)
        cookie.maxAge = 0

        response.addCookie(cookie)
    }

    //만들 때랑 지울 때 같은 속성이어야 브라우저가 지우니까 같이 씀
    private fun applyCookieSettings(cookie: Cookie) {
        cookie.path = "/"
        cookie.isHttpOnly = true
        cookie.secure = cookieSecure

        //비어 있으면 도메인 지정 안 함 (응답한 주소 기준으로 저장됨)
        if (!cookieDomain.isNullOrBlank()) {
            cookie.domain = cookieDomain
        }

        //비어 있으면 SameSite 안 붙임
        if (!cookieSameSite.isNullOrBlank()) {
            cookie.setAttribute("SameSite", cookieSameSite)
        }
    }

    fun getHeader(name: String?, defaultValue: String?): String? {
        return request.getHeader(name)
            ?.takeIf { headerValue -> !headerValue.isBlank() }
            ?: defaultValue
    }
}
