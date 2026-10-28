package com.merge.backend.global.security

import java.net.URI
import java.net.URISyntaxException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration

// 쿠키는 다른 사이트에서도 자동으로 실려 오므로, 요청이 우리 프론트에서 왔는지 직접 확인 (CSRF 방지)
// CORS 설정이 바뀌어도 막히도록 CORS와 따로 검사한다
@Component
class RequestOriginValidator(
    // CORS와 같은 허용 목록을 씀 (application.yml, 배포 때 환경변수로 덮어씀)
    @Value("\${custom.cors.allowed-origins}") allowedOrigins: List<String>,
) {

    // 비교는 Spring CORS와 같은 규칙(끝의 / 무시, 대소문자 무시)으로 함.
    // 규칙이 다르면 CORS는 통과하는데 로그아웃만 거부될 수 있음. 와일드카드 패턴은 넣지 않음 (정확한 주소만)
    private val corsConfiguration = CorsConfiguration().apply { this.allowedOrigins = allowedOrigins }

    // Origin이 있으면 Origin만 보고, 없을 때만 Referer의 출처로 비교. 둘 다 없으면 거부
    fun isAllowed(origin: String?, referer: String?): Boolean {
        if (!origin.isNullOrBlank()) {
            // "null"은 목록에 없으니 거부됨
            return isAllowedOrigin(origin)
        }

        val refererOrigin = originOf(referer) ?: return false
        return isAllowedOrigin(refererOrigin)
    }

    // 목록에 "*"가 있어도 전부 허용하지 않음
    private fun isAllowedOrigin(origin: String): Boolean {
        val matched = corsConfiguration.checkOrigin(origin)
        return matched != null && matched != CorsConfiguration.ALL
    }

    // "http://localhost:3000/login?x=1" → "http://localhost:3000"
    private fun originOf(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }

        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }

        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        // 기본 포트(http 80, https 443)는 브라우저가 Origin에 적지 않으므로 빼고 비교
        val port = if (uri.port == -1 || uri.port == defaultPort(scheme)) "" else ":${uri.port}"

        return "$scheme://$host$port"
    }

    private fun defaultPort(scheme: String): Int =
        when (scheme.lowercase()) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
}
