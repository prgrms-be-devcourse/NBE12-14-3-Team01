
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

    // 소속 Guard (ML-5 §5 규칙 4): 소속 여부가 결과를 바꾸는 Command가 마지막에 소속 Row를 FOR SHARE로 잠그고 최신 leftAt을 다시 읽는다.
    // 퇴사 확정의 소속 FOR UPDATE와 직렬화된다. 영속성 Context의 예전 값이 아니라 DB 최신 값을 읽도록 Scalar Native 조회를 쓴다.
    // READ COMMITTED에서는 조건에 맞지 않는 Row의 Lock이 바로 풀리므로, 반드시 "현재 소속인 Row"와 일치하는 조건으로 센다 (1 = 현재 소속, Lock 유지)
    @Query(
        value = """
        SELECT COUNT(*)
        FROM workplace_member
        WHERE id = :memberId
          AND workplace_id = :workplaceId
          AND left_at IS NULL
        FOR SHARE
        """,
        nativeQuery = true,
    )
    fun countCurrentMemberForShare(
        @Param("memberId") memberId: Long,
        @Param("workplaceId") workplaceId: Long,
    ): Long

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
