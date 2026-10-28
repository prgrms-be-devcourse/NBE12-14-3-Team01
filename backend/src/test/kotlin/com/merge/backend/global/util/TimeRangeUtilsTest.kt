package com.merge.backend.global.util

import java.time.LocalDateTime
import java.time.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TimeRangeUtilsTest {

    @Test
    fun 시작_시각이_종료_시각보다_앞서면_유효한_범위이다() {
        // given
        val start = LocalDateTime.of(2026, 9, 16, 13, 59)

        val end = LocalDateTime.of(2026, 9, 16, 14, 0)

        // when
        val result = TimeRangeUtils.isValidRange(start, end)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun 시작_시각과_종료_시각이_같으면_유효하지_않다() {
        // given
        val start = LocalDateTime.of(2026, 9, 16, 14, 0)

        val end = LocalDateTime.of(2026, 9, 16, 14, 0)

        // when
        val result = TimeRangeUtils.isValidRange(start, end)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun 시작_시각이_종료_시각보다_늦으면_유효하지_않다() {
        // given
        val start = LocalDateTime.of(2026, 9, 16, 15, 0)

        val end = LocalDateTime.of(2026, 9, 16, 14, 0)

        // when
        val result = TimeRangeUtils.isValidRange(start, end)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun LocalTime_시작_시각이_종료_시각보다_앞서면_유효하다() {
        // given
        val start = LocalTime.of(13, 59)
        val end = LocalTime.of(14, 0)

        // when
        val result = TimeRangeUtils.isValidRange(start, end)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun LocalTime_시작과_종료가_같으면_유효하지_않다() {
        // given
        val start = LocalTime.of(14, 0)
        val end = LocalTime.of(14, 0)

        // when
        val result = TimeRangeUtils.isValidRange(start, end)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun LocalTime_시작이_종료보다_늦으면_유효하지_않다() {
        // given
        val start = LocalTime.of(15, 0)
        val end = LocalTime.of(14, 0)

        // when
        val result = TimeRangeUtils.isValidRange(start, end)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun 다음_날까지_이어지는_시간_범위도_순서가_맞으면_유효하다() {
        // given
        val start = LocalDateTime.of(2026, 9, 16, 23, 0)

        val end = LocalDateTime.of(2026, 9, 17, 1, 0)

        // when
        val result = TimeRangeUtils.isValidRange(start, end)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun 두_시간_구간이_부분적으로_겹치면_true를_반환한다() {
        // given
        val aStart = LocalDateTime.of(2026, 9, 16, 10, 0)
        val aEnd = LocalDateTime.of(2026, 9, 16, 14, 0)

        val bStart = LocalDateTime.of(2026, 9, 16, 13, 0)
        val bEnd = LocalDateTime.of(2026, 9, 16, 18, 0)

        // when
        val result = TimeRangeUtils.overlaps(aStart, aEnd, bStart, bEnd)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun 첫_구간의_끝과_두번째_구간의_시작이_같으면_겹치지_않는다() {
        // given
        val aStart = LocalDateTime.of(2026, 9, 16, 10, 0)
        val aEnd = LocalDateTime.of(2026, 9, 16, 14, 0)

        val bStart = LocalDateTime.of(2026, 9, 16, 14, 0)
        val bEnd = LocalDateTime.of(2026, 9, 16, 18, 0)

        // when
        val result = TimeRangeUtils.overlaps(aStart, aEnd, bStart, bEnd)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun 두_시간_구간이_완전히_떨어져_있으면_false를_반환한다() {
        // given
        val aStart = LocalDateTime.of(2026, 9, 16, 10, 0)
        val aEnd = LocalDateTime.of(2026, 9, 16, 14, 0)

        val bStart = LocalDateTime.of(2026, 9, 16, 15, 0)
        val bEnd = LocalDateTime.of(2026, 9, 16, 18, 0)

        // when
        val result = TimeRangeUtils.overlaps(aStart, aEnd, bStart, bEnd)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun 한_시간_구간이_다른_구간을_포함하면_true를_반환한다() {
        // given
        val aStart = LocalDateTime.of(2026, 9, 16, 10, 0)
        val aEnd = LocalDateTime.of(2026, 9, 16, 18, 0)

        val bStart = LocalDateTime.of(2026, 9, 16, 12, 0)
        val bEnd = LocalDateTime.of(2026, 9, 16, 14, 0)

        // when
        val result = TimeRangeUtils.overlaps(aStart, aEnd, bStart, bEnd)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun 두_시간_구간이_완전히_같으면_true를_반환한다() {
        // given
        val aStart = LocalDateTime.of(2026, 9, 16, 10, 0)
        val aEnd = LocalDateTime.of(2026, 9, 16, 14, 0)

        val bStart = LocalDateTime.of(2026, 9, 16, 10, 0)
        val bEnd = LocalDateTime.of(2026, 9, 16, 14, 0)

        // when
        val result = TimeRangeUtils.overlaps(aStart, aEnd, bStart, bEnd)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun LocalTime_두_시간_구간이_겹치면_true를_반환한다() {
        // given
        val aStart = LocalTime.of(10, 0)
        val aEnd = LocalTime.of(14, 0)

        val bStart = LocalTime.of(13, 0)
        val bEnd = LocalTime.of(18, 0)

        // when
        val result = TimeRangeUtils.overlaps(aStart, aEnd, bStart, bEnd)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun LocalTime_한_구간의_끝과_다른_구간의_시작이_같으면_겹치지_않는다() {
        // given
        val aStart = LocalTime.of(10, 0)
        val aEnd = LocalTime.of(14, 0)

        val bStart = LocalTime.of(14, 0)
        val bEnd = LocalTime.of(18, 0)

        // when
        val result = TimeRangeUtils.overlaps(aStart, aEnd, bStart, bEnd)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun LocalTime_두_시간_구간이_떨어져_있으면_false를_반환한다() {
        // given
        val aStart = LocalTime.of(10, 0)
        val aEnd = LocalTime.of(14, 0)

        val bStart = LocalTime.of(15, 0)
        val bEnd = LocalTime.of(18, 0)

        // when
        val result = TimeRangeUtils.overlaps(aStart, aEnd, bStart, bEnd)

        // then
        assertThat(result).isFalse()
    }
}
