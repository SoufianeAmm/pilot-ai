package com.flowpilot.identity.internal.application;

import java.time.Instant;

/** A signed JWT access token. */
public record AccessToken(String value, Instant expiresAt) {
}
