package com.merge.backend.domain.workplace.repository

import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

/** H2 미리보기 전용 조회. V3의 Command·Repository 계약은 변경하지 않는다. */
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

    // 두 후보 상태를 같은 조회에서 읽어 PENDING/ACCEPTED 결과 합산 중복을 방지한다.
    @Query(
        """
        SELECT c
        FROM SubstituteCandidate c
        JOIN FETCH c.member m
        JOIN FETCH c.request r
        JOIN FETCH r.shift s
        JOIN FETCH s.schedule
        WHERE r.id IN :requestIds
          AND c.status IN (com.merge.backend.domain.substitute.entity.CandidateStatus.PENDING,
                           com.merge.backend.domain.substitute.entity.CandidateStatus.ACCEPTED)
        ORDER BY r.id ASC, c.id ASC
        """
    )
    fun findAffectedCandidates(@Param("requestIds") requestIds: List<Long>): List<SubstituteCandidate>
}
