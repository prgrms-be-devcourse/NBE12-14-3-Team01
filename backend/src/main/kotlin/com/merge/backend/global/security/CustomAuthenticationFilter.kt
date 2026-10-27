package com.merge.backend.global.security

import com.merge.backend.domain.user.exception.UserErrorCode
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.exception.ErrorCode
import com.merge.backend.global.rq.Rq
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

@Component
class CustomAuthenticationFilter(
    private val userService: UserService,
    private val rq: Rq,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {

        // context-path(/api/v1)가 포함된 전체 경로 기준 — API 요청이 아니면 인증 체크 자체를 스킵
        if (!request.requestURI.startsWith("/api/")) {
            filterChain.doFilter(request, response)
            return
        }

        // 회원가입/로그인/리프레시는 인증 없이 호출되는 Public API라서 필터 검증 대상에서 제외
        if (listOf("/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh")
                .contains(request.requestURI)
        ) {
            filterChain.doFilter(request, response)
            return
        }

        // Authorization: Bearer {accessToken} 형식 — 표준 Bearer 토큰 방식
        val headerAuthorization = rq.getHeader("Authorization", "")

        // 기본값이 ""라서 null은 안 나오지만 Kotlin 타입(String?) 때문에 null도 같이 검사
        if (headerAuthorization.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        if (!headerAuthorization.startsWith("Bearer ")) {
            writeErrorResponse(response, UserErrorCode.INVALID_AUTHORIZATION_HEADER)
            return
        }

        val accessToken = headerAuthorization.substring("Bearer ".length)
        val payload = userService.payloadOrNull(accessToken)

        if (payload == null) {
            writeErrorResponse(response, UserErrorCode.INVALID_ACCESS_TOKEN)
            return
        }

        // payloadOrNull이 id·email·name을 항상 채워서 돌려줌
        val id = payload["id"] as Long
        val email = payload["email"] as String
        val name = payload["name"] as String

        val securityUser: UserDetails = SecurityUser(email, "", listOf(), id, name)

        val authentication: Authentication = UsernamePasswordAuthenticationToken(
            securityUser,
            securityUser.password,
            securityUser.authorities,
        )

        SecurityContextHolder.getContext().authentication = authentication

        filterChain.doFilter(request, response)
    }

    //공통 예외 처리 범위 밖이라서 직접 응답
    private fun writeErrorResponse(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.getHttpStatus().value()
        response.contentType = "application/json; charset=UTF-8"
        response.writer.write(
            objectMapper.writeValueAsString(
                ApiResponse.error(errorCode.getCode(), errorCode.getMessage()),
            ),
        )
    }
}
