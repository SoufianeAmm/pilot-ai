package com.flowpilot.identity.internal.application;

import java.util.UUID;

/** Result of a successful refresh-token rotation. */
public record Rotated(UUID userId, IssuedRefreshToken newToken) {
}
