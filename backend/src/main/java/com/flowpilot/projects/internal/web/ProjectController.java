package com.flowpilot.projects.internal.web;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.organizations.OrgPermission;
import com.flowpilot.organizations.OrganizationAccess;
import com.flowpilot.projects.ProjectAccess;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.internal.application.ProjectService;
import com.flowpilot.projects.internal.domain.ProjectStatus;
import com.flowpilot.projects.internal.web.ProjectDtos.CreateProjectRequest;
import com.flowpilot.projects.internal.web.ProjectDtos.ProjectResponse;
import com.flowpilot.projects.internal.web.ProjectDtos.UpdateProjectRequest;
import com.flowpilot.shared.security.AuthenticatedUser;
import com.flowpilot.shared.web.PageResponse;
import com.flowpilot.shared.web.Pageables;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects")
class ProjectController {

    /** Sort fields the client is allowed to use (everything else is ignored). */
    private static final Set<String> SORT_WHITELIST = Set.of("name", "status", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by("updatedAt").descending();

    private final ProjectService projects;
    private final ProjectAccess projectAccess;
    private final OrganizationAccess organizationAccess;

    ProjectController(ProjectService projects, ProjectAccess projectAccess, OrganizationAccess organizationAccess) {
        this.projects = projects;
        this.projectAccess = projectAccess;
        this.organizationAccess = organizationAccess;
    }

    /** Create a project in the organization. Requires the org-level {@code PROJECT_CREATE} permission. */
    @PostMapping
    ResponseEntity<ProjectResponse> create(@PathVariable UUID orgId,
                                           @Valid @RequestBody CreateProjectRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = organizationAccess.requirePermission(orgId, user.userId(), OrgPermission.PROJECT_CREATE);
        var project = projects.create(scope, request.key(), request.name(), request.description(),
                request.startDate(), request.targetDate());
        var body = ProjectResponse.from(project);
        return ResponseEntity.created(URI.create("/api/v1/orgs/" + orgId + "/projects/" + project.getId()))
                .body(body);
    }

    /** List the organization's projects, optionally filtered by status. */
    @GetMapping
    PageResponse<ProjectResponse> list(@PathVariable UUID orgId,
                                       @RequestParam(name = "status", required = false) ProjectStatus status,
                                       Pageable pageable,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = organizationAccess.requireMembership(orgId, user.userId());
        Pageable safe = Pageables.restrictSort(pageable, SORT_WHITELIST, DEFAULT_SORT);
        var page = projects.list(scope, status, safe);
        return PageResponse.of(page, ProjectResponse::from);
    }

    @GetMapping("/{projectId}")
    ProjectResponse get(@PathVariable UUID orgId, @PathVariable UUID projectId,
                        @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requireMembership(orgId, projectId, user.userId());
        return ProjectResponse.from(projects.get(scope));
    }

    @PatchMapping("/{projectId}")
    ProjectResponse update(@PathVariable UUID orgId, @PathVariable UUID projectId,
                           @Valid @RequestBody UpdateProjectRequest request,
                           @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requirePermission(orgId, projectId, user.userId(), ProjectPermission.PROJECT_UPDATE);
        return ProjectResponse.from(projects.update(scope, request.name(), request.description(),
                request.status(), request.startDate(), request.targetDate(), request.version()));
    }
}
