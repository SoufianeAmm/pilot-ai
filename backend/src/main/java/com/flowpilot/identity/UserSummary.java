package com.flowpilot.identity;

import java.util.UUID;

public record UserSummary(UUID id, String email, String displayName) {
}
