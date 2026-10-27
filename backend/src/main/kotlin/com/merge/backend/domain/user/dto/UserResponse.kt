package com.merge.backend.domain.user.dto

import com.merge.backend.domain.user.entity.User

data class UserResponse(
    val id: Long?,
    val email: String,
    val name: String,
) {

    companion object {
        // Java 컨트롤러가 UserResponse.from(user)로 부르므로 static으로 노출
        @JvmStatic
        fun from(user: User): UserResponse {
            // Java와 같게 id가 null이면 그대로 null로 응답
            return UserResponse(
                user.id,
                user.email,
                user.name,
            )
        }
    }
}
