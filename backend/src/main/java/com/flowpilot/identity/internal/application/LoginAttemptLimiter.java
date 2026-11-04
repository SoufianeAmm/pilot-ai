package com.flowpilot.identity.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.flowpilot.identity.internal.application.AuthExceptions.TooManyLoginAttemptsException;

/**
 * Limits failed logins per account+IP (protects one account) and per IP (stops password spraying
 * across many accounts). Counters live in PostgreSQL, so the limit holds across all replicas.
 * Fixed window: the counter resets when the window has passed. A check and a later failure are
 * not atomic, so concurrent attempts may exceed the limit by a few; that is acceptable here.
 */
@Component
public class LoginAttemptLimiter {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final int maxFailures;
    private final int maxFailuresPerIp;
    private final Duration window;

    public LoginAttemptLimiter(JdbcClient jdbc, Clock clock, IdentityProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.maxFailures = properties.login().maxFailures();
        this.maxFailuresPerIp = properties.login().maxFailuresPerIp();
        this.window = properties.login().window();
    }

    /** @throws TooManyLoginAttemptsException with the remaining lockout time */
    public void checkAllowed(String email, String ip) {
        Instant now = clock.instant();
        check(accountKey(email, ip), maxFailures, now);
        check(ipKey(ip), maxFailuresPerIp, now);
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        increment(accountKey(email, ip), now);
        increment(ipKey(ip), now);
    }

    /** After a successful login the account counter starts again; the IP counter is kept. */
    public void reset(String email, String ip) {
        jdbc.sql("DELETE FROM login_attempt WHERE attempt_key = :key").param("key", accountKey(email, ip)).update();
    }

    private void check(String key, int limit, Instant now) {
        jdbc.sql("SELECT failures, window_start FROM login_attempt WHERE attempt_key = :key")
                .param("key", key)
                .query((rs, row) -> new Counter(rs.getInt("failures"),
                        rs.getObject("window_start", OffsetDateTime.class).toInstant()))
                .optional()
                .filter(counter -> counter.windowStart().plus(window).isAfter(now) && counter.failures() >= limit)
                .ifPresent(counter -> {
                    throw new TooManyLoginAttemptsException(Duration.between(now, counter.windowStart().plus(window)));
                });
    }

    private void increment(String key, Instant now) {
        jdbc.sql("""
                        INSERT INTO login_attempt (attempt_key, failures, window_start)
                        VALUES (:key, 1, :now)
                        ON CONFLICT (attempt_key) DO UPDATE SET
                            failures     = CASE WHEN login_attempt.window_start <= :expired THEN 1
                                                ELSE login_attempt.failures + 1 END,
                            window_start = CASE WHEN login_attempt.window_start <= :expired THEN :now
                                                ELSE login_attempt.window_start END
                        """)
                .param("key", key)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("expired", OffsetDateTime.ofInstant(now.minus(window), ZoneOffset.UTC))
                .update();
    }

    private static String accountKey(String email, String ip) {
        return "acct:" + email + "|" + ip;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private record Counter(int failures, Instant windowStart) {
    }
}
