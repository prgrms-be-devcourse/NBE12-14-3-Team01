package com.merge.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Test Context는 TestTimeConfig가 Clock을 덮어쓰므로 운영 설정을 직접 생성해 확인한다
class TimeConfigTest {

    private final TimeConfig timeConfig = new TimeConfig();

    @Test
    @DisplayName("운영 Clock은 Asia/Seoul 시간대의 시스템 시계다")
    void clockUsesSeoulZone() {
        Clock clock = timeConfig.clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("Auditing 시각은 주입받은 Clock 기준 LocalDateTime이다")
    void auditingDateTimeProviderUsesClock() {
        Clock fixed = Clock.fixed(Instant.parse("2026-09-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

        assertThat(timeConfig.auditingDateTimeProvider(fixed).getNow())
            .contains(LocalDateTime.of(2026, 9, 15, 10, 0));
    }
}
