package com.flowpilot.organizations;

import java.util.UUID;

import com.flowpilot.shared.web.ApiException;

/**
 * Proof that {@code userId} is a member of {@code orgId} with {@code role}, obtained from
 * {@link OrganizationAccess}. Services that work on organization data take an OrgScope instead of a
 * raw orgId, so they cannot be called without the membership check.
 */
public record OrgScope(UUID orgId, UUID userId, OrgRole role) {

    public boolean has(OrgPermission permission) {
        return role.has(permission);
    }

    /** @throws ApiException 403 if the role lacks the permission */
    public void require(OrgPermission permission) {
        if (!has(permission)) {
            throw ApiException.forbidden("You do not have permission to perform this action.");
        }
    }
}
