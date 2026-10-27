package com.merge.backend.global.config

import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAccessor
import java.util.Optional
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider

@Configuration
class TimeConfig {

    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))

    @Bean
    fun auditingDateTimeProvider(clock: Clock): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(LocalDateTime.now(clock)) }
}
