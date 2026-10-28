package com.merge.backend.domain.user.repository

import com.merge.backend.domain.user.entity.User
import jakarta.persistence.LockModeType
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {

    fun findByEmail(email: String?): Optional<User>

    // Java 텍스트 블록과 같은 문자열이 되도록 줄마다 \n을 붙임
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "SELECT u\n" +
            "FROM User u\n" +
            "WHERE u.id = :userId\n"
    )
    fun findByIdForUpdate(
        @Param("userId") userId: Long?,
    ): Optional<User>

    // 비교와 삭제를 한 번에: 저장된 값이 요청 토큰과 같을 때만 지움 (다른 기기의 새 로그인은 안 지움)
    @Modifying
    @Query(
        "UPDATE User u\n" +
            "SET u.refreshToken = NULL\n" +
            "WHERE u.id = :userId AND u.refreshToken = :refreshToken\n"
    )
    fun clearRefreshTokenIfMatches(
        @Param("userId") userId: Long,
        @Param("refreshToken") refreshToken: String,
    ): Int
}
