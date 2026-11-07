package com.flowpilot.projects.internal.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.organizations.OrgPermission;
import com.flowpilot.organizations.OrgScope;
import com.flowpilot.organizations.OrganizationAccess;
import com.flowpilot.projects.ProjectAccess;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.ProjectRole;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.projects.internal.persistence.ProjectMemberRepository;
import com.flowpilot.projects.internal.persistence.ProjectRepository;
import com.flowpilot.shared.web.ApiException;

/**
 * Implements {@link ProjectAccess}: the two-layer check (organization membership, then project
 * membership) and the implicit-MANAGER rule for org OWNER/ADMIN. Mirrors
 * {@code OrganizationAccessService}: 404 (never 403) for non-members, audited 403 for denials.
 */
@Service
@Transactional(readOnly = true)
class ProjectAccessService implements ProjectAccess {

    private final OrganizationAccess organizationAccess;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final AuditTrail audit;

    ProjectAccessService(OrganizationAccess organizationAccess, ProjectRepository projects,
                         ProjectMemberRepository members, AuditTrail audit) {
        this.organizationAccess = organizationAccess;
        this.projects = projects;
        this.members = members;
        this.audit = audit;
    }

    @Override
    public ProjectScope requireMembership(UUID orgId, UUID projectId, UUID userId) {
        if (orgId == null || projectId == null || userId == null) {
            throw notFound();
        }
        // Layer 1: the caller must be a member of the organization (404 otherwise).
        OrgScope orgScope = organizationAccess.requireMembership(orgId, userId);
        // The project must exist and belong to this organization (404 otherwise).
        if (projects.findByIdAndOrgId(projectId, orgId).isEmpty()) {
            throw notFound();
        }
        // Layer 2: org OWNER/ADMIN carry PROJECT_MANAGE_ALL -> implicit MANAGER, no row needed.
        if (orgScope.has(OrgPermission.PROJECT_MANAGE_ALL)) {
            return new ProjectScope(orgId, projectId, userId, ProjectRole.MANAGER);
        }
        // Everyone else needs an explicit project_member row.
        return members.findByProjectIdAndUserId(projectId, userId)
                .map(m -> new ProjectScope(orgId, projectId, userId, m.getRole()))
                .orElseThrow(ProjectAccessService::notFound);
    }

    @Override
    public ProjectScope requirePermission(UUID orgId, UUID projectId, UUID userId,
                                          ProjectPermission permission) {
        ProjectScope scope = requireMembership(orgId, projectId, userId);
        if (!scope.has(permission)) {
            audit.record(AuditEvent.denied(AuditActions.ACCESS_DENIED, orgId, userId)
                    .withDetail("permission", permission.name())
                    .withDetail("scope", "project:" + projectId));
            scope.require(permission); // throws 403
        }
        return scope;
    }

    @Override
    public Optional<ProjectRole> findRole(UUID orgId, UUID projectId, UUID userId) {
        if (orgId == null || projectId == null || userId == null) {
            return Optional.empty();
        }
        return organizationAccess.findRole(orgId, userId).flatMap(orgRole -> {
            if (projects.findByIdAndOrgId(projectId, orgId).isEmpty()) {
                return Optional.empty();
            }
            if (orgRole.has(OrgPermission.PROJECT_MANAGE_ALL)) {
                return Optional.of(ProjectRole.MANAGER);
            }
            return members.findByProjectIdAndUserId(projectId, userId).map(m -> m.getRole());
        });
    }

    /** Same answer for "does not exist" and "exists but you are not a member". */
    private static ApiException notFound() {
        return ApiException.notFound("Project not found.");
    }
}
