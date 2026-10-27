package com.merge.backend.standard

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Date

object Ut {

    // Java 호출부가 Ut.jwt.toString(...) 형태로 부르므로 소문자 이름을 유지한다
    @Suppress("ClassName")
    object jwt {

        @JvmStatic
        fun toString(secret: String, expireSeconds: Long, body: Map<String, Any>): String {
            val claimsBuilder = Jwts.claims()
            body.forEach { (key, value) -> claimsBuilder.add(key, value) }
            val claims = claimsBuilder.build()

            val issuedAt = Date()
            val expiration = Date(issuedAt.time + expireSeconds)

            // Java String.getBytes()와 같은 기본 Charset을 쓴다
            val secretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charset.defaultCharset()))

            return Jwts.builder()
                .claims(claims)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey)
                .compact()
        }

        @JvmStatic
        fun isValid(jwt: String, secretPattern: String): Boolean {
            val secretKey = Keys.hmacShaKeyFor(secretPattern.toByteArray(StandardCharsets.UTF_8))

            return try {
                Jwts.parser().verifyWith(secretKey).build().parse(jwt)
                true
            } catch (e: Exception) {
                false
            }
        }

        @JvmStatic
        fun payloadOrNull(jwt: String?, secretPattern: String): Map<String, Any>? {
            // Java 원본은 null이면 isValid에서 예외를 잡아 false → null 반환
            if (jwt == null) {
                return null
            }

            val secretKey = Keys.hmacShaKeyFor(secretPattern.toByteArray(StandardCharsets.UTF_8))

            if (!isValid(jwt, secretPattern)) {
                return null
            }

            @Suppress("UNCHECKED_CAST")
            return Jwts.parser().verifyWith(secretKey).build().parse(jwt).payload as Map<String, Any>
        }
    }
}
