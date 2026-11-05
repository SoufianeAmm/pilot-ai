package com.flowpilot.projects;

import static com.flowpilot.projects.ProjectPermission.AI_ASK;
import static com.flowpilot.projects.ProjectPermission.ANALYTICS_VIEW;
import static com.flowpilot.projects.ProjectPermission.DOCUMENT_MANAGE_ANY;
import static com.flowpilot.projects.ProjectPermission.DOCUMENT_UPLOAD;
import static com.flowpilot.projects.ProjectPermission.MEMBER_MANAGE;
import static com.flowpilot.projects.ProjectPermission.MILESTONE_MANAGE;
import static com.flowpilot.projects.ProjectPermission.PROJECT_DELETE;
import static com.flowpilot.projects.ProjectPermission.PROJECT_READ;
import static com.flowpilot.projects.ProjectPermission.PROJECT_UPDATE;
import static com.flowpilot.projects.ProjectPermission.TASK_CREATE;
import static com.flowpilot.projects.ProjectPermission.TASK_EDIT;
import static com.flowpilot.projects.ProjectPermission.TASK_MANAGE_ANY;

import java.util.EnumSet;
import java.util.Set;

/**
 * Project roles, from most to least powerful. The permission matrix is the one in
 * {@code docs/01-architecture.md} section 6 (Authorization and tenant isolation).
 *
 * <p>A user whose organization role carries {@code PROJECT_MANAGE_ALL} (organization OWNER or ADMIN)
 * receives an implicit {@link #MANAGER} scope on every project of the organization, even without a
 * {@code project_member} row. That rule is applied in {@code ProjectAccessService}, not here.
 */
public enum ProjectRole {

    MANAGER(2, EnumSet.allOf(ProjectPermission.class)),
    CONTRIBUTOR(1, EnumSet.of(
            PROJECT_READ, TASK_CREATE, TASK_EDIT, DOCUMENT_UPLOAD, AI_ASK, ANALYTICS_VIEW)),
    VIEWER(0, EnumSet.of(
            PROJECT_READ, AI_ASK, ANALYTICS_VIEW));

    private final int rank;
    private final Set<ProjectPermission> permissions;

    ProjectRole(int rank, Set<ProjectPermission> permissions) {
        this.rank = rank;
        this.permissions = Set.copyOf(permissions);
    }

    public Set<ProjectPermission> permissions() {
        return permissions;
    }

    public boolean has(ProjectPermission permission) {
        return permissions.contains(permission);
    }

    public boolean outranks(ProjectRole other) {
        return rank > other.rank;
    }
}
