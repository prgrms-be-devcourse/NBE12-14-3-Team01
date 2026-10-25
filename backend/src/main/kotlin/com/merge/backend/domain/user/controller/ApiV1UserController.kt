package com.merge.backend.domain.user.controller

import com.merge.backend.domain.user.dto.LoginRequest
import com.merge.backend.domain.user.dto.LoginResponse
import com.merge.backend.domain.user.dto.RefreshResponse
import com.merge.backend.domain.user.dto.SignupRequest
import com.merge.backend.domain.user.dto.UserResponse
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class ApiV1UserController(
    private val userService: UserService,
    private val rq: Rq,
) {

    @PostMapping("/signup")
    fun signup(
        @Valid @RequestBody request: SignupRequest,
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val user = userService.join(request.email, request.password, request.name)

        val response = UserResponse.from(user)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("201", "회원가입이 완료되었습니다.", response))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val user = userService.login(request.email, request.password)
        val accessToken = userService.genAccessToken(user)
        val refreshToken = userService.issueRefreshToken(user)

        rq.addCookie("refreshToken", refreshToken)

        val response = LoginResponse(UserResponse.from(user), accessToken)

        return ResponseEntity.ok(ApiResponse.success("200", "로그인에 성공했습니다", response))
    }

    @PostMapping("/refresh")
    fun refresh(): ResponseEntity<ApiResponse<RefreshResponse>> {
        val refreshToken = rq.getCookieValue("refreshToken", "")
        val accessToken = userService.refreshAccessToken(refreshToken)

        return ResponseEntity.ok(
            ApiResponse.success("200", "Access Token이 재발급되었습니다", RefreshResponse(accessToken)),
        )
    }

    @GetMapping("/me")
    fun me(): ResponseEntity<ApiResponse<UserResponse>> {
        val user = userService.getById(rq.getActorId())
        val response = UserResponse.from(user)

        return ResponseEntity.ok(ApiResponse.success("200", "현재 사용자 정보를 조회했습니다", response))
    }
}
