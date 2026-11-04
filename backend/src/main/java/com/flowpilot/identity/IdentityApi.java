package com.flowpilot.identity;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only user lookups for other modules. */
public interface IdentityApi {

    Optional<UserSummary> findUser(UUID userId);

    Map<UUID, UserSummary> findUsers(Set<UUID> userIds);

    /** The email is matched case-insensitively. */
    Optional<UserSummary> findByEmail(String email);
}
