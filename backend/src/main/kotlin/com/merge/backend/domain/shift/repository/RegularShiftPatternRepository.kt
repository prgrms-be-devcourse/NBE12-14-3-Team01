package com.merge.backend.domain.shift.repository

import com.merge.backend.domain.shift.entity.RegularShiftPattern
import java.time.DayOfWeek
import org.springframework.data.jpa.repository.JpaRepository

interface RegularShiftPatternRepository : JpaRepository<RegularShiftPattern, Long> {
    fun findByMemberIdAndDayOfWeek(memberId: Long?, dayOfWeek: DayOfWeek?): List<RegularShiftPattern>

    fun findByMemberWorkplaceIdAndMemberLeftAtIsNull(workplaceId: Long?): List<RegularShiftPattern>
}
