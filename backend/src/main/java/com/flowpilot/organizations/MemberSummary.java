package com.flowpilot.organizations;

import java.time.Instant;
import java.util.UUID;

public record MemberSummary(UUID userId, String displayName, String email, OrgRole role, Instant joinedAt) {
}
