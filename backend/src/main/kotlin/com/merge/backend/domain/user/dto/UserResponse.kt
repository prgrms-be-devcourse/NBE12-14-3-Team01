package com.merge.backend.domain.user.dto

import com.merge.backend.domain.user.entity.User

data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
) {

    companion object {
        // Java 컨트롤러가 UserResponse.from(user)로 부르므로 static으로 노출
        @JvmStatic
        fun from(user: User): UserResponse {
            // 저장된 User만 들어옴 (id는 저장 후 생성)
            return UserResponse(
                requireNotNull(user.id),
                user.email,
                user.name,
            )
        }
    }
}
