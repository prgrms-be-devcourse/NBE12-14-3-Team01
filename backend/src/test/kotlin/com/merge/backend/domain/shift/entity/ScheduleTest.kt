package com.merge.backend.domain.shift.entity

import com.merge.backend.domain.workplace.entity.Workplace
import java.time.DayOfWeek
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ScheduleTest {

    @Test
    fun 새로운_스케줄은_DRAFT_상태로_생성된다() {
        val workplace = mock<Workplace>()
        val weekStartDate = LocalDate.of(2026, 9, 21)

        val schedule = Schedule(workplace, weekStartDate)

        assertThat(schedule.workplace).isSameAs(workplace)
        assertThat(schedule.weekStartDate).isEqualTo(weekStartDate)
        assertThat(schedule.status).isEqualTo(ScheduleStatus.DRAFT)
        assertThat(schedule.publishedAt).isNull()
    }

    @Test
    fun 월요일은_주_시작일과_같다() {
        // given
        val workplace = mock<Workplace>()
        val schedule = Schedule(workplace, LocalDate.of(2026, 9, 21))

        // when
        val result = schedule.resolveDate(DayOfWeek.MONDAY)

        // then
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 21))
    }

    @Test
    fun 수요일은_주_시작일로부터_이틀_뒤이다() {
        // given
        val workplace = mock<Workplace>()
        val schedule = Schedule(workplace, LocalDate.of(2026, 9, 21))

        // when
        val result = schedule.resolveDate(DayOfWeek.WEDNESDAY)

        // then
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 23))
    }

    @Test
    fun 일요일은_주_시작일로부터_육일_뒤이다() {
        // given
        val workplace = mock<Workplace>()
        val schedule = Schedule(workplace, LocalDate.of(2026, 9, 21))

        // when
        val result = schedule.resolveDate(DayOfWeek.SUNDAY)

        // then
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 27))
    }
}
