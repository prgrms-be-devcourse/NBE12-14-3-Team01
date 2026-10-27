package com.merge.backend.domain.workplace.repository

import com.merge.backend.domain.shift.entity.Shift
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
}
