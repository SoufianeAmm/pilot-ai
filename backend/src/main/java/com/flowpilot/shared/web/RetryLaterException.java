package com.flowpilot.shared.web;

import java.net.URI;
import java.time.Duration;

import org.springframework.http.HttpStatus;

/**
 * An error the client can recover from by trying again later (429, 503). The problem response
 * carries a Retry-After header with {@link #retryAfter()} rounded up to whole seconds.
 */
public class RetryLaterException extends ApiException {

    private final Duration retryAfter;

    public RetryLaterException(HttpStatus status, URI type, String title, String detail, Duration retryAfter) {
        super(status, type, title, detail);
        this.retryAfter = retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    }

    /** 503: a dependency (for example the AI model) is busy or down. */
    public static RetryLaterException unavailable(String detail, Duration retryAfter) {
        return new RetryLaterException(HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.SERVICE_UNAVAILABLE,
                "Service unavailable", detail, retryAfter);
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
