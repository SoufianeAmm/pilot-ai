package com.flowpilot.shared.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All business code takes the current time from this {@link Clock} instead of calling
 * {@code Instant.now()} directly, so tests can pin time (e.g. for overdue-task rules).
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
