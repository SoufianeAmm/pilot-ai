package com.flowpilot.shared.web;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/** 429 Too Many Requests; the response carries a Retry-After header. */
public class RateLimitedException extends RetryLaterException {

    public RateLimitedException(String detail, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, ProblemTypes.RATE_LIMITED, "Too many requests", detail, retryAfter);
    }
}
