package com.merge.backend.global.config

import java.time.Instant
import java.time.ZoneId
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestTimeConfig {

    @Bean
    @Primary
    fun testClock(): TestClock =
        TestClock(
            Instant.parse("2026-09-15T01:00:00Z"),
            ZoneId.of("Asia/Seoul"),
        )
}
