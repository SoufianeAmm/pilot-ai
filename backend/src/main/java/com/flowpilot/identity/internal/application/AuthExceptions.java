package com.flowpilot.identity.internal.application;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.flowpilot.shared.web.ApiException;
import com.flowpilot.shared.web.ProblemTypes;
import com.flowpilot.shared.web.RateLimitedException;

/** Authentication errors. Messages are shown to clients and are deliberately generic. */
public final class AuthExceptions {

    private AuthExceptions() {
    }

    public static class EmailAlreadyRegisteredException extends ApiException {
        public EmailAlreadyRegisteredException() {
            super(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Conflict", "An account with this email already exists.");
        }
    }

    public static class WeakPasswordException extends ApiException {
        public WeakPasswordException(String detail) {
            super(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION, "Validation failed", detail);
        }
    }

    /** Same message whether the email is unknown, the account is disabled or the password is wrong. */
    public static class InvalidCredentialsException extends ApiException {
        public InvalidCredentialsException() {
            super(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "Unauthorized", "Invalid email or password.");
        }
    }

    public static class InvalidRefreshTokenException extends ApiException {
        public InvalidRefreshTokenException() {
            super(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "Unauthorized",
                    "The session has expired. Please log in again.");
        }
    }

    /** An already-rotated refresh token was presented again: the whole token family has been revoked. */
    public static class RefreshTokenReusedException extends ApiException {
        private final transient UUID userId;

        public RefreshTokenReusedException(UUID userId) {
            super(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "Unauthorized",
                    "The session has expired. Please log in again.");
            this.userId = userId;
        }

        public UUID userId() {
            return userId;
        }
    }

    public static class TooManyLoginAttemptsException extends RateLimitedException {
        public TooManyLoginAttemptsException(Duration retryAfter) {
            super("Too many failed login attempts. Try again later.", retryAfter);
        }
    }

    /** Thrown by JwtService; the security filter turns it into a 401. */
    public static class InvalidAccessTokenException extends RuntimeException {
        public InvalidAccessTokenException(Throwable cause) {
            super("Invalid access token", cause);
        }
    }
}
