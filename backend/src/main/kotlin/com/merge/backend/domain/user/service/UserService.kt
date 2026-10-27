package com.merge.backend.domain.user.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.exception.UserErrorCode
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.global.exception.BusinessException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authTokenService: AuthTokenService,
) {

    fun join(email: String?, password: String?, name: String?): User {
        if (userRepository.findByEmail(email).isPresent) {
            throw BusinessException(UserErrorCode.DUPLICATE_EMAIL)
        }
        val passwordHash = passwordEncoder.encode(password)
        // 요청 검증(@NotBlank)을 통과한 값이라 null이 오지 않음
        val newUser = User(requireNotNull(email), requireNotNull(passwordHash), requireNotNull(name))
        return userRepository.save(newUser)
    }

    fun login(email: String?, password: String?): User {
        val user = userRepository.findByEmail(email)
            .orElseThrow { BusinessException(UserErrorCode.INVALID_CREDENTIALS) }

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw BusinessException(UserErrorCode.INVALID_CREDENTIALS)
        }

        return user
    }

    fun getById(id: Long?): User {
        // findById는 null을 받지 않음. 호출하는 곳은 로그인한 사용자 id만 넘겨서 null이 오지 않음
        return userRepository.findById(requireNotNull(id))
            .orElseThrow { BusinessException(UserErrorCode.AUTHENTICATION_REQUIRED) }
    }

    @Transactional
    fun getByIdForUpdate(id: Long?): User {
        return userRepository.findByIdForUpdate(id)
            .orElseThrow { BusinessException(UserErrorCode.AUTHENTICATION_REQUIRED) }
    }

    fun genAccessToken(user: User): String {
        return authTokenService.genAccessToken(user)
    }

    @Transactional
    fun issueRefreshToken(user: User): String {
        val refreshToken = authTokenService.genRefreshToken(user)
        user.updateRefreshToken(refreshToken)
        userRepository.save(user)
        return refreshToken
    }

    fun refreshAccessToken(refreshToken: String?): String {
        val userId = authTokenService.refreshTokenUserIdOrNull(refreshToken)
            ?: throw BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN)

        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN) }

        if (refreshToken != user.refreshToken) {
            throw BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN)
        }

        return authTokenService.genAccessToken(user)
    }

    fun payloadOrNull(jwt: String?): Map<String, Any>? {
        return authTokenService.payloadOrNull(jwt)
    }
}
