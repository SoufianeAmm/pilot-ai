package com.flowpilot.identity.internal.web;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.flowpilot.identity.UserSummary;
import com.flowpilot.identity.internal.application.AccessToken;

/** Request and response bodies of the auth endpoints. */
final class AuthDtos {

    private AuthDtos() {
    }

    record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                           @NotNull @Size(min = 12, max = 128) String password,
                           @NotBlank @Size(max = 80) String displayName) {

        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + ", password=***, displayName=" + displayName + "]";
        }
    }

    /** No format validation on login: every bad input gets the same 401 answer. */
    record LoginRequest(@NotBlank @Size(max = 254) String email,
                        @NotBlank @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=***]";
        }
    }

    record TokenResponse(String accessToken, long expiresIn, String tokenType) {

        static TokenResponse from(AccessToken token, Clock clock) {
            long seconds = Math.max(0, Duration.between(clock.instant(), token.expiresAt()).toSeconds());
            return new TokenResponse(token.value(), seconds, "Bearer");
        }
    }

    record UserResponse(UUID id, String email, String displayName) {

        static UserResponse from(UserSummary user) {
            return new UserResponse(user.id(), user.email(), user.displayName());
        }
    }
}
