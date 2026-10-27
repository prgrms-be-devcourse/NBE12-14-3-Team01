package com.merge.backend.domain.user.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.exception.UserErrorCode
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.global.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.verify
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class UserServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var authTokenService: AuthTokenService

    @InjectMocks
    private lateinit var userService: UserService

    @Test
    fun 이미_가입된_이메일로_회원가입하면_예외가_발생한다() {
        val existingUser = User("test@example.com", "hash", "이름")
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(existingUser))

        assertThatThrownBy { userService.join("test@example.com", "password", "이름") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(UserErrorCode.DUPLICATE_EMAIL)
    }

    @Test
    fun 새_이메일로_회원가입하면_비밀번호를_암호화해서_저장한다() {
        given(userRepository.findByEmail("new@example.com")).willReturn(Optional.empty())
        given(passwordEncoder.encode("password")).willReturn("encodedPassword")
        // save에 넘긴 User를 그대로 돌려줌
        given(userRepository.save(any<User>()))
            .willAnswer { invocation -> invocation.getArgument<User>(0) }

        val result = userService.join("new@example.com", "password", "이름")

        assertThat(result.email).isEqualTo("new@example.com")
        assertThat(result.passwordHash).isEqualTo("encodedPassword")
        assertThat(result.name).isEqualTo("이름")
        verify(userRepository).save(result)
    }

    @Test
    fun 이메일과_비밀번호가_맞으면_로그인에_성공한다() {
        val user = User("test@example.com", "encodedPassword", "이름")
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user))
        given(passwordEncoder.matches("password", "encodedPassword")).willReturn(true)

        val result = userService.login("test@example.com", "password")

        assertThat(result).isEqualTo(user)
    }

    @Test
    fun 존재하지_않는_이메일로_로그인하면_예외가_발생한다() {
        given(userRepository.findByEmail("no-such@example.com")).willReturn(Optional.empty())

        assertThatThrownBy { userService.login("no-such@example.com", "password") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(UserErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun 비밀번호가_틀리면_로그인에_실패한다() {
        val user = User("test@example.com", "encodedPassword", "이름")
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user))
        given(passwordEncoder.matches("wrongPassword", "encodedPassword")).willReturn(false)

        assertThatThrownBy { userService.login("test@example.com", "wrongPassword") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(UserErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun 리프레시_토큰을_발급하면_유저에게_저장된다() {
        val user = User("test@example.com", "hash", "이름")
        given(authTokenService.genRefreshToken(user)).willReturn("new-refresh-token")

        val result = userService.issueRefreshToken(user)

        assertThat(result).isEqualTo("new-refresh-token")
        assertThat(user.refreshToken).isEqualTo("new-refresh-token")
        verify(userRepository).save(user)
    }

    @Test
    fun 유효한_리프레시_토큰이면_새_액세스_토큰을_발급한다() {
        val user = User(1L, "test@example.com", "이름")
        user.updateRefreshToken("refresh-token-value")

        given(authTokenService.refreshTokenUserIdOrNull("refresh-token-value")).willReturn(1L)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(authTokenService.genAccessToken(user)).willReturn("new-access-token")

        val result = userService.refreshAccessToken("refresh-token-value")

        assertThat(result).isEqualTo("new-access-token")
    }

    @Test
    fun 리프레시_토큰_서명이_유효하지_않으면_예외가_발생한다() {
        given(authTokenService.refreshTokenUserIdOrNull("invalid")).willReturn(null)

        assertThatThrownBy { userService.refreshAccessToken("invalid") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN)
    }

    @Test
    fun 리프레시_토큰의_유저가_존재하지_않으면_예외가_발생한다() {
        given(authTokenService.refreshTokenUserIdOrNull("refresh-token-value")).willReturn(1L)
        given(userRepository.findById(1L)).willReturn(Optional.empty())

        assertThatThrownBy { userService.refreshAccessToken("refresh-token-value") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN)
    }

    @Test
    fun DB에_저장된_리프레시_토큰과_다르면_예외가_발생한다() {
        val user = User(1L, "test@example.com", "이름")
        user.updateRefreshToken("old-refresh-token")

        given(authTokenService.refreshTokenUserIdOrNull("stale-refresh-token")).willReturn(1L)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))

        assertThatThrownBy { userService.refreshAccessToken("stale-refresh-token") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN)
    }

    @Test
    fun id로_유저를_조회한다() {
        val user = User(1L, "test@example.com", "이름")
        given(userRepository.findById(1L)).willReturn(Optional.of(user))

        val result = userService.getById(1L)

        assertThat(result).isEqualTo(user)
    }

    @Test
    fun 존재하지_않는_id로_조회하면_예외가_발생한다() {
        given(userRepository.findById(1L)).willReturn(Optional.empty())

        assertThatThrownBy { userService.getById(1L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(UserErrorCode.AUTHENTICATION_REQUIRED)
    }
}
