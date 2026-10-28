package com.merge.backend.global.security

import java.net.URI
import java.net.URISyntaxException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

// 쿠키는 다른 사이트에서도 자동으로 실려 오므로, 요청이 우리 프론트에서 왔는지 직접 확인 (CSRF 방지)
// CORS 설정이 바뀌어도 막히도록 CORS와 따로 검사한다
@Component
class RequestOriginValidator(
    // CORS와 같은 허용 목록을 씀 (application.yml, 배포 때 환경변수로 덮어씀)
    @Value("\${custom.cors.allowed-origins}") private val allowedOrigins: List<String>,
) {

    // Origin이 있으면 Origin만 보고, 없을 때만 Referer의 출처로 비교. 둘 다 없으면 거부
    fun isAllowed(origin: String?, referer: String?): Boolean {
        if (!origin.isNullOrBlank()) {
            // 정확히 같은 값만 허용 ("null"은 목록에 없으니 거부됨)
            return origin in allowedOrigins
        }

        val refererOrigin = originOf(referer) ?: return false
        return refererOrigin in allowedOrigins
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
        val port = if (uri.port == -1) "" else ":${uri.port}"

        return "$scheme://$host$port"
    }
}
