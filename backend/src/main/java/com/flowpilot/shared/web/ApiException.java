package com.flowpilot.shared.web;

import java.net.URI;

import org.springframework.http.HttpStatus;

/**
 * Business error that maps to an RFC 9457 problem response. The message becomes the
 * problem "detail" and is shown to clients, so it must never contain internal data.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final URI type;
    private final String title;

    public ApiException(HttpStatus status, URI type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Resource not found", detail);
    }

    /** 400 for a request that is well-formed but breaks a business rule (e.g. end date before start date). */
    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION, "Validation failed", detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Conflict", detail);
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "Unauthorized", detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Forbidden", detail);
    }

    public HttpStatus status() {
        return status;
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }
}
