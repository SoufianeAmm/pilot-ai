package com.flowpilot.identity;

import java.time.Instant;
import java.util.UUID;

/** Published after a new account has been created. */
public record UserRegisteredEvent(UUID userId, String email, Instant occurredAt) {
}
