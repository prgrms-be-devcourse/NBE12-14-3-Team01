package com.merge.backend.domain.user.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.standard.Ut
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AuthTokenService {

    @Value("\${custom.jwt.secret-key}")
    private lateinit var secretPattern: String

    @Value("\${custom.jwt.expireMills}")
    private var expireMills: Long = 0

    @Value("\${custom.jwt.refresh-expire-mills}")
    private var refreshExpireMills: Long = 0

    // Java가 같은 패키지(UserServiceTest)에서 부르므로 public (internal은 이름이 바뀜)
    fun genAccessToken(user: User): String =
        Ut.jwt.toString(
            secretPattern,
            expireMills,
            java.util.Map.of<String, Any?>("id", user.id, "email", user.email, "name", user.name),
        )

    fun genRefreshToken(user: User): String =
        Ut.jwt.toString(
            secretPattern,
            refreshExpireMills,
            // jti: 로그인마다 다른 값이라 같은 1초에 다시 로그인해도 토큰이 달라짐
            java.util.Map.of<String, Any?>("id", user.id, "type", "refresh", "jti", UUID.randomUUID().toString()),
        )

    fun payloadOrNull(jwt: String?): Map<String, Any>? {
        val payload = Ut.jwt.payloadOrNull(jwt, secretPattern)

        // refreshToken 등 email/name이 없는 토큰은 accessToken이 아니므로 거부
        if (payload == null || payload["email"] == null || payload["name"] == null) {
            return null
        }

        val id = (payload["id"] as Number).toLong()
        val email = payload["email"] as String
        val name = payload["name"] as String

        return java.util.Map.of("id", id, "email", email, "name", name)
    }

    fun refreshTokenUserIdOrNull(jwt: String?): Long? {
        val payload = Ut.jwt.payloadOrNull(jwt, secretPattern)

        if (payload == null || "refresh" != payload["type"]) {
            return null
        }

        return (payload["id"] as Number).toLong()
    }
}
