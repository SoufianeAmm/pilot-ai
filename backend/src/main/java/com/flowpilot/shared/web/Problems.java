package com.flowpilot.shared.web;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** Builds problem responses consistently (type, title, request id, Retry-After). */
public final class Problems {

    private Problems() {
    }

    public static ResponseEntity<ProblemDetail> response(ApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setType(ex.type());
        problem.setTitle(ex.title());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.status());
        if (ex instanceof RateLimitedException limited) {
            long seconds = Math.max(1, (long) Math.ceil(limited.retryAfter().toMillis() / 1000.0));
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        return builder.body(withRequestId(problem));
    }

    public static ProblemDetail withRequestId(ProblemDetail problem) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
