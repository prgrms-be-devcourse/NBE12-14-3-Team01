
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

    @EntityGraph(attributePaths = ["user"])
    fun findByIdAndWorkplace_Id(
        memberId: Long,
        workplaceId: Long
    ): Optional<WorkplaceMember>

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

    // 대타 생성 소속 Guard: 요청자·후보 소속을 ID 오름차순으로 FOR SHARE (ML-5 §5 규칙 1·4)
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query(
        """
        SELECT wm
        FROM WorkplaceMember wm
        WHERE wm.id IN :memberIds
        ORDER BY wm.id ASC
        """
    )
    fun findAllByIdInForShare(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<WorkplaceMember>

    // 소속 Guard: Lock 뒤 그 근무지의 현재 소속인지 DB 값으로 다시 확인 (1차 Cache의 옛 Entity 값을 쓰지 않음, ML-5 §5 규칙 5)
    @Query(
        """
        SELECT COUNT(wm) > 0
        FROM WorkplaceMember wm
        WHERE wm.id = :memberId
          AND wm.workplace.id = :workplaceId
          AND wm.leftAt IS NULL
        """
    )
    fun existsCurrentMember(
        @Param("memberId") memberId: Long,
        @Param("workplaceId") workplaceId: Long,
    ): Boolean

    // 소속 Guard: 위와 같고 EMPLOYEE만 (대타 후보·수락자 조건)
    @Query(
        """
        SELECT wm.id
        FROM WorkplaceMember wm
        WHERE wm.id IN :memberIds
          AND wm.workplace.id = :workplaceId
          AND wm.leftAt IS NULL
          AND wm.role = com.merge.backend.domain.workplace.entity.WorkplaceRole.EMPLOYEE
        """
    )
    fun findCurrentEmployeeIdsIn(
        @Param("memberIds") memberIds: Collection<Long>,
        @Param("workplaceId") workplaceId: Long,
    ): List<Long>

    fun existsByWorkplaceAndUser(
        workplace: Workplace?,
        user: User?
    ): Boolean

    fun findByWorkplace_IdAndUser_IdAndLeftAtIsNull(
        workplaceId: Long?,
        userId: Long?
    ): Optional<WorkplaceMember>

    @EntityGraph(attributePaths = ["workplace"])
    fun findAllByUser_IdAndLeftAtIsNull(
        userId: Long?
    ): List<WorkplaceMember>

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

interface WorkplaceMemberScope {
    val workplaceId: Long?
    val userId: Long?
}
