package com.flowpilot.shared.web;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/** 429 Too Many Requests; the response carries a Retry-After header. */
public class RateLimitedException extends ApiException {

    private final Duration retryAfter;

    public RateLimitedException(String detail, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, ProblemTypes.RATE_LIMITED, "Too many requests", detail);
        this.retryAfter = retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
