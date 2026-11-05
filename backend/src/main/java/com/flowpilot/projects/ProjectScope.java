package com.flowpilot.projects;

import java.util.UUID;

import com.flowpilot.shared.web.ApiException;

/**
 * Proof that {@code userId} may access {@code projectId} in organization {@code orgId} with
 * {@code role}, obtained from {@link ProjectAccess}. Services that touch project data take a
 * {@code ProjectScope} instead of raw ids, so they cannot be called without the membership and
 * permission check having already happened.
 *
 * <p>This mirrors {@link com.flowpilot.organizations.OrgScope}: the same "pass the scope, not the id"
 * discipline that makes the compiler enforce authorization at every call site.
 */
public record ProjectScope(UUID orgId, UUID projectId, UUID userId, ProjectRole role) {

    public boolean has(ProjectPermission permission) {
        return role.has(permission);
    }

    /** @throws ApiException 403 if the role lacks the permission */
    public void require(ProjectPermission permission) {
        if (!has(permission)) {
            throw ApiException.forbidden("You do not have permission to perform this action.");
        }
    }
}
