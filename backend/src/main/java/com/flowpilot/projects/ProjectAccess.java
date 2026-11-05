package com.flowpilot.projects;

import java.util.Optional;
import java.util.UUID;

import com.flowpilot.shared.web.ApiException;

/**
 * The single place that decides project access. Call it at the start of every endpoint under
 * {@code /api/v1/orgs/{orgId}/projects/{projectId}/...}.
 *
 * <p>The check is two layers, defense in depth:
 * <ol>
 *   <li>Organization membership, through
 *       {@link com.flowpilot.organizations.OrganizationAccess#requireMembership}. A non-member of the
 *       organization gets 404 immediately.</li>
 *   <li>Project membership. A user whose organization role carries {@code PROJECT_MANAGE_ALL}
 *       (organization OWNER or ADMIN) gets an implicit {@link ProjectRole#MANAGER} scope with no
 *       {@code project_member} row. Everyone else needs an explicit {@code project_member} row.</li>
 * </ol>
 *
 * <p>A user who is not a project member gets <strong>404, never 403</strong>, whether or not the
 * project exists, so project ids cannot be probed (same rule as organizations).
 */
public interface ProjectAccess {

    /** @throws ApiException 404 if the user is not a member of the project */
    ProjectScope requireMembership(UUID orgId, UUID projectId, UUID userId);

    /**
     * @throws ApiException 404 if the user is not a member of the project,
     *         403 if the role lacks the permission (the denial is audited)
     */
    ProjectScope requirePermission(UUID orgId, UUID projectId, UUID userId, ProjectPermission permission);

    Optional<ProjectRole> findRole(UUID orgId, UUID projectId, UUID userId);
}
