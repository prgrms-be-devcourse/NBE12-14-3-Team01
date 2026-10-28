package com.merge.backend.domain.substitute.repository

import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

// 기존 #92 조회와 V2의 잠근 범위 내 처리가 같은 활성 기준을 사용한다.
private const val MEMBER_LEAVE_REQUEST_SCOPE = """
        FROM SubstituteRequest r
        WHERE r.status IN (com.merge.backend.domain.substitute.entity.RequestStatus.OPEN,
                           com.merge.backend.domain.substitute.entity.RequestStatus.ACCEPTED)
          AND r.shift.startAt > :now
          AND EXISTS (
                SELECT c.id
                FROM SubstituteCandidate c
                WHERE c.request = r
                  AND c.member.id = :memberId
                  AND c.status IN (com.merge.backend.domain.substitute.entity.CandidateStatus.PENDING,
                                   com.merge.backend.domain.substitute.entity.CandidateStatus.ACCEPTED)
          )
"""

interface SubstituteRequestRepository : JpaRepository<SubstituteRequest, Long> {

    fun existsByShift_IdAndStatusIn(shiftId: Long, statuses: Collection<RequestStatus>): Boolean

    // SUB-05
    // 현재 소속(leftAt IS NULL)으로 보낸 요청만 조회. 이전 소속 기간의 요청 Row는 지우지 않고 목록에서만 빠짐 (ML-4-4)
    @EntityGraph(attributePaths = ["shift.schedule.workplace"])
    fun findAllByRequesterMember_User_IdAndRequesterMember_LeftAtIsNullOrderByCreateDateDesc(
        userId: Long,
    ): List<SubstituteRequest>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT r
        FROM SubstituteRequest r
        WHERE r.id = :requestId
        """,
    )
    fun findByIdForUpdate(@Param("requestId") requestId: Long): SubstituteRequest?

    // Lock 없이 ID만 조회 (Entity를 영속성 Context에 올리지 않음)
    @Query(
        """
        SELECT new com.merge.backend.domain.substitute.repository.SubstituteRequestScope(sh.id, w.id)
        FROM SubstituteRequest r
        JOIN r.shift sh
        JOIN sh.schedule sc
        JOIN sc.workplace w
        WHERE r.id = :requestId
        """,
    )
    fun findScopeById(@Param("requestId") requestId: Long): SubstituteRequestScope?

    // 해당 근무의 진행 중(OPEN·ACCEPTED) 요청을 잠금
    // 여러 건이면 ID 순서로 잠가서 다른 Transaction과 순서가 엇갈리지 않게 함
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT r
        FROM SubstituteRequest r
        WHERE r.shift.id = :shiftId
          AND r.status IN (com.merge.backend.domain.substitute.entity.RequestStatus.OPEN,
                           com.merge.backend.domain.substitute.entity.RequestStatus.ACCEPTED)
        ORDER BY r.id ASC
        """,
    )
    fun findActiveByShiftIdForUpdate(@Param("shiftId") shiftId: Long): List<SubstituteRequest>

    // 이 소속이 대기·수락 후보로 걸린 진행 중 요청 (근무 시작 전)을 잠금
    // 여러 건이면 ID 순서로 잠가서 다른 Transaction과 순서가 엇갈리지 않게 함
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "SELECT r " + MEMBER_LEAVE_REQUEST_SCOPE + " ORDER BY r.id ASC",
    )
    fun findActiveByCandidateMemberIdForUpdate(
        @Param("memberId") memberId: Long,
        @Param("now") now: LocalDateTime,
    ): List<SubstituteRequest>

    @Query("SELECT r.id " + MEMBER_LEAVE_REQUEST_SCOPE + " ORDER BY r.id ASC")
    fun findActiveIdsByCandidateMemberId(
        @Param("memberId") memberId: Long,
        @Param("now") now: LocalDateTime,
    ): List<Long>

    // V2가 Request→User→소속을 모두 잠근 뒤 사용한다. 새 FOR UPDATE를 발행하지 않는다.
    @Query("SELECT r " + MEMBER_LEAVE_REQUEST_SCOPE + " AND r.id IN :requestIds ORDER BY r.id ASC")
    fun findActiveByCandidateMemberIdWithinIds(
        @Param("memberId") memberId: Long,
        @Param("now") now: LocalDateTime,
        @Param("requestIds") requestIds: Set<Long>,
    ): List<SubstituteRequest>

    // Lock 뒤 상태를 DB 값으로 확인 (Lock 전에 읽힌 Entity는 옛 값일 수 있음)
    @Query("SELECT r.status FROM SubstituteRequest r WHERE r.id = :requestId")
    fun findStatusById(@Param("requestId") requestId: Long): RequestStatus?

    // 해당 근무지의 모든 대체근무 요청 조회
    // 근무 시간 순으로 정렬하되, 시간이 같으면 id 기준으로 정렬
    @Query(
        """
        SELECT s
        FROM SubstituteRequest s
        JOIN FETCH s.shift sh
        JOIN FETCH s.requesterMember rm
        JOIN FETCH rm.user
        JOIN FETCH rm.workplace
        WHERE s.shift.schedule.workplace.id = :workplaceId
          AND s.status IN (com.merge.backend.domain.substitute.entity.RequestStatus.OPEN,
                            com.merge.backend.domain.substitute.entity.RequestStatus.ACCEPTED)
          AND s.shift.schedule.status =
                  com.merge.backend.domain.shift.entity.ScheduleStatus.PUBLISHED
          AND s.shift.status = com.merge.backend.domain.shift.entity.ShiftStatus.SCHEDULED
          AND s.shift.startAt > :now
        ORDER BY s.shift.startAt ASC, s.id ASC
        """,
    )
    fun findAllByWorkplaceId(
        @Param("workplaceId") workplaceId: Long,
        @Param("now") now: LocalDateTime,
    ): List<SubstituteRequest>
}
