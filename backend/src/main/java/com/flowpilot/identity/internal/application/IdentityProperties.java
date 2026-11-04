package com.flowpilot.identity.internal.application;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bound from {@code flowpilot.security.*}; invalid or missing values stop the application at startup. */
@Validated
@ConfigurationProperties("flowpilot.security")
public record IdentityProperties(@NotEmpty List<String> allowedOrigins,
                                 @Valid @NotNull Jwt jwt,
                                 @Valid @NotNull Refresh refreshToken,
                                 @Valid @NotNull Login login) {

    /** @param secret base64-encoded HMAC key of at least 32 bytes; comes from FLOWPILOT_JWT_SECRET */
    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl) {
    }

    /** @param reuseGrace a just-rotated token reused within this window is rejected without revoking the family (two tabs refreshing at once) */
    public record Refresh(@NotNull Duration ttl, boolean cookieSecure, @NotNull Duration reuseGrace) {
    }

    public record Login(@Positive int maxFailures, @Positive int maxFailuresPerIp, @NotNull Duration window) {
    }
}
