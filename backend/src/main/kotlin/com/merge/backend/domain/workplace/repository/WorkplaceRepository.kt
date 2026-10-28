package com.merge.backend.domain.workplace.repository

import com.merge.backend.domain.workplace.entity.Workplace
import jakarta.persistence.LockModeType
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WorkplaceRepository : JpaRepository<Workplace, Long> {
    fun existsByInviteCode(inviteCode: String?): Boolean

    fun findByInviteCode(inviteCode: String?): Optional<Workplace>

    @Query("SELECT w.id FROM Workplace w WHERE w.inviteCode = :inviteCode")
    fun findIdByInviteCode(@Param("inviteCode") inviteCode: String?): Long?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w\nFROM Workplace w\nWHERE w.id = :workplaceId\n")
    fun findByIdForUpdate(
        @Param("workplaceId") workplaceId: Long?
    ): Optional<Workplace>
}
