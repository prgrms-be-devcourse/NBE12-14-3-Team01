package com.merge.backend.domain.user.dto

data class LoginResponse(
    val user: UserResponse,
    val accessToken: String,
)
