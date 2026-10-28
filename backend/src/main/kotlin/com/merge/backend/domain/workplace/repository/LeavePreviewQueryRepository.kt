package com.merge.backend.domain.workplace.repository

import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

/** 퇴사 미리보기 전용 조회. V3·V4 Repository와 상태 변경 규약은 유지한다. */
interface LeavePreviewQueryRepository : Repository<Shift, Long> {
    @Query(
        """
        SELECT s
        FROM Shift s
        JOIN FETCH s.schedule sc
        WHERE s.member.id = :memberId
          AND sc.workplace.id = :workplaceId
          AND (sc.status = com.merge.backend.domain.shift.entity.ScheduleStatus.DRAFT
               OR (sc.status = com.merge.backend.domain.shift.entity.ScheduleStatus.PUBLISHED
                   AND s.status = com.merge.backend.domain.shift.entity.ShiftStatus.SCHEDULED
                   AND s.endAt > :now))
        ORDER BY s.startAt ASC, s.id ASC
        """
    )
    fun findAffectedShifts(
        @Param("workplaceId") workplaceId: Long,
        @Param("memberId") memberId: Long,
        @Param("now") now: LocalDateTime
    ): List<Shift>

    // 근무지 전체 요청 대신 대상의 담당 근무 또는 대상이 후보인 요청만 읽는다.
    // to-one 연관을 함께 읽어 DTO 변환 중 요청별 추가 조회를 방지한다.
    @Query(
        """
        SELECT r
        FROM SubstituteRequest r
        JOIN FETCH r.shift s
        JOIN FETCH s.schedule sc
        JOIN FETCH s.member
        JOIN FETCH r.requesterMember
        WHERE sc.workplace.id = :workplaceId
          AND sc.status = com.merge.backend.domain.shift.entity.ScheduleStatus.PUBLISHED
          AND s.status = com.merge.backend.domain.shift.entity.ShiftStatus.SCHEDULED
          AND s.startAt > :now
          AND r.status IN (com.merge.backend.domain.substitute.entity.RequestStatus.OPEN,
                           com.merge.backend.domain.substitute.entity.RequestStatus.ACCEPTED)
          AND (s.member.id = :memberId OR EXISTS (
              SELECT c.id FROM SubstituteCandidate c
              WHERE c.request = r AND c.member.id = :memberId
                AND c.status IN (com.merge.backend.domain.substitute.entity.CandidateStatus.PENDING,
                                 com.merge.backend.domain.substitute.entity.CandidateStatus.ACCEPTED)
          ))
        ORDER BY s.startAt ASC, r.id ASC
        """
    )
    fun findAffectedRequests(
        @Param("workplaceId") workplaceId: Long,
        @Param("memberId") memberId: Long,
        @Param("now") now: LocalDateTime
    ): List<SubstituteRequest>

    @Query(
        """
        SELECT c
        FROM SubstituteCandidate c
        JOIN FETCH c.member m
        JOIN FETCH m.workplace
        JOIN FETCH c.request r
        JOIN FETCH r.shift s
        JOIN FETCH s.schedule
        WHERE r.id IN :requestIds
          AND m.workplace.id = s.schedule.workplace.id
          AND c.status IN (com.merge.backend.domain.substitute.entity.CandidateStatus.PENDING,
                           com.merge.backend.domain.substitute.entity.CandidateStatus.ACCEPTED)
        ORDER BY r.id ASC, c.id ASC
        """
    )
    fun findAffectedCandidates(@Param("requestIds") requestIds: List<Long>): List<SubstituteCandidate>
}
