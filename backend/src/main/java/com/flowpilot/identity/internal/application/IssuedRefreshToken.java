package com.flowpilot.identity.internal.application;

import java.time.Instant;

/** The raw refresh token value: returned once, sent only in an HttpOnly cookie, never stored. */
public record IssuedRefreshToken(String rawValue, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedRefreshToken[rawValue=***, expiresAt=" + expiresAt + "]";
    }
}
