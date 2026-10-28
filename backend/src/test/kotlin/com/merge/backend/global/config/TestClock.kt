package com.merge.backend.global.config

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

// Test에서 현재 시각을 고정하고 바꾸는 Clock (Kotlin Test들이 setInstant로 시각을 바꾼다)
class TestClock(
    private var instant: Instant,
    private val zone: ZoneId,
) : Clock() {

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = TestClock(instant, zone)

    override fun instant(): Instant = instant

    fun setInstant(instant: Instant) {
        this.instant = instant
    }
}
