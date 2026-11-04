package com.flowpilot.identity.internal.application;

import java.time.Duration;
import java.util.List;

/** IdentityProperties for unit tests, matching application.yml defaults. */
final class TestIdentityProperties {

    static final String SECRET = "dGVzdC1zZWNyZXQtZm9yLWZsb3dwaWxvdC1pbnRlZ3JhdGlvbi10ZXN0cw==";

    private TestIdentityProperties() {
    }

    static IdentityProperties withSecret(String secret) {
        return new IdentityProperties(List.of("http://localhost:5173"),
                new IdentityProperties.Jwt(secret, "flowpilot", Duration.ofMinutes(15)),
                new IdentityProperties.Refresh(Duration.ofDays(7), true, Duration.ofSeconds(10)),
                new IdentityProperties.Login(5, 30, Duration.ofMinutes(15)));
    }

    static IdentityProperties defaults() {
        return withSecret(SECRET);
    }
}
