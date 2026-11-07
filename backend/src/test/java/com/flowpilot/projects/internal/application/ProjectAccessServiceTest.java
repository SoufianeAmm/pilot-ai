package com.flowpilot.projects.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flowpilot.audit.AuditTrail;
import com.flowpilot.organizations.OrgRole;
import com.flowpilot.organizations.OrgScope;
import com.flowpilot.organizations.OrganizationAccess;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.ProjectRole;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.projects.internal.domain.Project;
import com.flowpilot.projects.internal.domain.ProjectMember;
import com.flowpilot.projects.internal.persistence.ProjectMemberRepository;
import com.flowpilot.projects.internal.persistence.ProjectRepository;
import com.flowpilot.shared.web.ApiException;

/**
 * Verifies the {@link ProjectAccessService} authorization rules from docs/01-architecture.md
 * section 6: the two-layer check (org membership then project membership) and the implicit-MANAGER
 * rule for org OWNER/ADMIN. Pure Mockito unit test: no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock private OrganizationAccess organizationAccess;
    @Mock private ProjectRepository projects;
    @Mock private ProjectMemberRepository members;
    @Mock private AuditTrail audit;

    private ProjectAccessService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProjectAccessService(organizationAccess, projects, members, audit);
    }

    @Test
    void orgOwnerGetsImplicitManagerEvenWithoutProjectMemberRow() {
        when(organizationAccess.requireMembership(orgId, userId))
                .thenReturn(new OrgScope(orgId, userId, OrgRole.OWNER));
        when(projects.findByIdAndOrgId(projectId, orgId)).thenReturn(Optional.of(mock(Project.class)));

        ProjectScope scope = service.requireMembership(orgId, projectId, userId);

        assertThat(scope.role()).isEqualTo(ProjectRole.MANAGER);
        // the implicit rule means no project_member lookup is needed
        verify(members, never()).findByProjectIdAndUserId(any(), any());
    }

    @Test
    void orgMemberWithProjectRowGetsTheRowRole() {
        when(organizationAccess.requireMembership(orgId, userId))
                .thenReturn(new OrgScope(orgId, userId, OrgRole.MEMBER));
        when(projects.findByIdAndOrgId(projectId, orgId)).thenReturn(Optional.of(mock(Project.class)));
        when(members.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(
                new ProjectMember(projectId, userId, orgId, ProjectRole.CONTRIBUTOR, 10, Instant.now())));

        ProjectScope scope = service.requireMembership(orgId, projectId, userId);

        assertThat(scope.role()).isEqualTo(ProjectRole.CONTRIBUTOR);
    }

    @Test
    void orgMemberWithoutProjectRowIsNotFound() {
        when(organizationAccess.requireMembership(orgId, userId))
                .thenReturn(new OrgScope(orgId, userId, OrgRole.MEMBER));
        when(projects.findByIdAndOrgId(projectId, orgId)).thenReturn(Optional.of(mock(Project.class)));
        when(members.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireMembership(orgId, projectId, userId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    void projectThatDoesNotExistInOrgIsNotFoundEvenForOrgOwner() {
        when(organizationAccess.requireMembership(orgId, userId))
                .thenReturn(new OrgScope(orgId, userId, OrgRole.OWNER));
        when(projects.findByIdAndOrgId(projectId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireMembership(orgId, projectId, userId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    void notAnOrgMemberIsNotFound() {
        when(organizationAccess.requireMembership(orgId, userId))
                .thenThrow(ApiException.notFound("Organization not found."));

        assertThatThrownBy(() -> service.requireMembership(orgId, projectId, userId))
                .isInstanceOf(ApiException.class);
        // the project lookup is never reached
        verify(projects, never()).findByIdAndOrgId(any(), any());
    }

    @Test
    void requirePermissionAuditsAndForbidsWhenRoleLacksIt() {
        // a VIEWER cannot create tasks
        when(organizationAccess.requireMembership(orgId, userId))
                .thenReturn(new OrgScope(orgId, userId, OrgRole.MEMBER));
        when(projects.findByIdAndOrgId(projectId, orgId)).thenReturn(Optional.of(mock(Project.class)));
        when(members.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(
                new ProjectMember(projectId, userId, orgId, ProjectRole.VIEWER, 0, Instant.now())));

        assertThatThrownBy(() -> service.requirePermission(orgId, projectId, userId,
                ProjectPermission.TASK_CREATE))
                .isInstanceOf(ApiException.class);
        // the denial is audited
        verify(audit).record(any());
    }
}
