package com.flowpilot.tasks.internal.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.projects.ProjectAccess;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.tasks.internal.application.DependencyService;
import com.flowpilot.shared.security.AuthenticatedUser;

/**
 * Add/remove a task dependency: "task depends on dependsOnTask". Adding a dependency that would
 * close a cycle returns 409 with the cycle path.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects/{projectId}/tasks/{taskId}/dependencies")
class DependencyController {

    private final DependencyService dependencies;
    private final ProjectAccess projectAccess;

    DependencyController(DependencyService dependencies, ProjectAccess projectAccess) {
        this.dependencies = dependencies;
        this.projectAccess = projectAccess;
    }

    @PutMapping("/{dependsOnId}")
    ResponseEntity<Void> add(@PathVariable UUID orgId, @PathVariable UUID projectId,
                             @PathVariable UUID taskId, @PathVariable UUID dependsOnId,
                             @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.TASK_EDIT);
        dependencies.add(scope, taskId, dependsOnId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{dependsOnId}")
    ResponseEntity<Void> remove(@PathVariable UUID orgId, @PathVariable UUID projectId,
                                @PathVariable UUID taskId, @PathVariable UUID dependsOnId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.TASK_EDIT);
        dependencies.remove(scope, taskId, dependsOnId);
        return ResponseEntity.noContent().build();
    }
}
