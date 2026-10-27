package com.merge.backend.domain.user.repository

import com.merge.backend.domain.user.entity.User
import jakarta.persistence.LockModeType
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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
}
