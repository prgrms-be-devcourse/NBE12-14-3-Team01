package com.merge.backend.domain.workplace.repository

import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import java.util.Optional
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface WorkplaceMemberRepository : JpaRepository<WorkplaceMember, Long> {

    @EntityGraph(attributePaths = ["user"])
    fun findByIdAndWorkplace_Id(
        memberId: Long,
        workplaceId: Long
    ): Optional<WorkplaceMember>

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
    fun findAllByWorkplace_IdAndLeftAtIsNull(
        workplaceId: Long?
    ): List<WorkplaceMember>

    fun existsByWorkplaceIdAndUserId(
        workplaceId: Long?,
        userId: Long?
    ): Boolean

    fun findAllByWorkplace_IdAndRoleAndLeftAtIsNull(
        workplaceId: Long?,
        role: WorkplaceRole?
    ): List<WorkplaceMember>
}
