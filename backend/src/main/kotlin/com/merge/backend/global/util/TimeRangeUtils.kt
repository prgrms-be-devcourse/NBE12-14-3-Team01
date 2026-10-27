package com.merge.backend.global.util

import java.time.LocalDateTime
import java.time.LocalTime

object TimeRangeUtils {

    // 시간 구간 자체의 유효성 검사
    @JvmStatic
    fun isValidRange(start: LocalDateTime, end: LocalDateTime): Boolean = start.isBefore(end)

    @JvmStatic
    fun isValidRange(start: LocalTime, end: LocalTime): Boolean = start.isBefore(end)

    // 유효한 두 시간 구간의 겹침 계산
    @JvmStatic
    fun overlaps(
        aStart: LocalDateTime,
        aEnd: LocalDateTime,
        bStart: LocalDateTime,
        bEnd: LocalDateTime,
    ): Boolean = aStart.isBefore(bEnd) && bStart.isBefore(aEnd)

    @JvmStatic
    fun overlaps(
        aStart: LocalTime,
        aEnd: LocalTime,
        bStart: LocalTime,
        bEnd: LocalTime,
    ): Boolean = aStart.isBefore(bEnd) && bStart.isBefore(aEnd)
}
