package com.merge.backend.domain.workplace.repository

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import jakarta.persistence.LockModeType
import java.util.Optional
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WorkplaceMemberRepository : JpaRepository<WorkplaceMember, Long> {
    @Query(
        """
        SELECT wm.workplace.id AS workplaceId, wm.user.id AS userId
        FROM WorkplaceMember wm
        WHERE wm.id = :memberId
        """
    )
    fun findScopeById(
        @Param("memberId") memberId: Long?,
    ): WorkplaceMemberScope?

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query(
        """
        SELECT wm
        FROM WorkplaceMember wm
        WHERE wm.id = :memberId
        """
    )
    fun findByIdForShare(
        @Param("memberId") memberId: Long?,
    ): Optional<WorkplaceMember>

    fun existsByWorkplaceAndUser(workplace: Workplace?, user: User?): Boolean

    fun findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
        workplaceId: Long?,
        userId: Long?
    ): Optional<WorkplaceMember>

    @EntityGraph(attributePaths = ["workplace"])
    fun findAllByUser_IdAndLeftAtIsNull(userId: Long?): List<WorkplaceMember>

    @EntityGraph(attributePaths = ["workplace"])
    fun findAllByUser_IdAndLeftAtIsNotNullOrderByLeftAtDescIdDesc(
        userId: Long
    ): List<WorkplaceMember>

    @EntityGraph(attributePaths = ["user"])
    fun findAllByWorkplace_IdAndLeftAtIsNull(workplaceId: Long?): List<WorkplaceMember>

    fun existsByWorkplaceIdAndUserId(workplaceId: Long?, userId: Long?): Boolean

    fun findAllByWorkplace_IdAndRoleAndLeftAtIsNull(
        workplaceId: Long?,
        role: WorkplaceRole?
    ): List<WorkplaceMember>
}

interface WorkplaceMemberScope {
    val workplaceId: Long?
    val userId: Long?
}
