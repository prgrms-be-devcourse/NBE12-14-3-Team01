package com.merge.backend.domain.substitute.repository

import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import java.util.Optional
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SubstituteRequestRepository : JpaRepository<SubstituteRequest, Long> {

    fun existsByShift_IdAndStatusIn(shiftId: Long?, statuses: Collection<RequestStatus>): Boolean

    @EntityGraph(attributePaths = ["shift.schedule.workplace"])
    fun findAllByRequesterMember_User_IdOrderByCreateDateDesc(
        userId: Long?,
    ): List<SubstituteRequest>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT r
        FROM SubstituteRequest r
        WHERE r.id = :requestId
        """,
    )
    fun findByIdForUpdate(@Param("requestId") requestId: Long?): Optional<SubstituteRequest>

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
        @Param("workplaceId") workplaceId: Long?,
        @Param("now") now: LocalDateTime,
    ): List<SubstituteRequest>
}
