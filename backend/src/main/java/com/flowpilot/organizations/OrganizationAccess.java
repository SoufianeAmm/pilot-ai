package com.flowpilot.organizations;

import java.util.Optional;
import java.util.UUID;

/**
 * The single place that decides organization membership. Call it at the start of every endpoint
 * under /api/v1/orgs/{orgId}/... (or use ProjectAccess, which calls it).
 *
 * <p>A user who is not a member gets 404, never 403, whether or not the organization exists,
 * so organization ids cannot be probed.
 */
public interface OrganizationAccess {

    /** @throws com.flowpilot.shared.web.ApiException 404 if the user is not a member */
    OrgScope requireMembership(UUID orgId, UUID userId);

    /**
     * @throws com.flowpilot.shared.web.ApiException 404 if the user is not a member,
     *         403 if the member's role lacks the permission (the denial is audited)
     */
    OrgScope requirePermission(UUID orgId, UUID userId, OrgPermission permission);

    Optional<OrgRole> findRole(UUID orgId, UUID userId);
}
