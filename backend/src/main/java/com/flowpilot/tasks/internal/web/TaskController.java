package com.flowpilot.tasks.internal.web;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.projects.ProjectAccess;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.tasks.internal.application.TaskService;
import com.flowpilot.tasks.internal.domain.TaskStatus;
import com.flowpilot.tasks.internal.web.TaskDtos.ChangeStatusRequest;
import com.flowpilot.tasks.internal.web.TaskDtos.CreateTaskRequest;
import com.flowpilot.tasks.internal.web.TaskDtos.TaskResponse;
import com.flowpilot.tasks.internal.web.TaskDtos.UpdateTaskRequest;
import com.flowpilot.shared.security.AuthenticatedUser;
import com.flowpilot.shared.web.PageResponse;
import com.flowpilot.shared.web.Pageables;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects/{projectId}/tasks")
class TaskController {

    private static final Set<String> SORT_WHITELIST = Set.of("title", "status", "priority", "dueDate", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by("createdAt").descending();

    private final TaskService taskService;
    private final ProjectAccess projectAccess;

    TaskController(TaskService taskService, ProjectAccess projectAccess) {
        this.taskService = taskService;
        this.projectAccess = projectAccess;
    }

    @PostMapping
    ResponseEntity<TaskResponse> create(@PathVariable UUID orgId, @PathVariable UUID projectId,
                                        @Valid @RequestBody CreateTaskRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.TASK_CREATE);
        var view = taskService.create(scope, request.title(), request.description(), request.priority(),
                request.assigneeId(), request.milestoneId(), request.dueDate(), request.estimatePoints());
        return ResponseEntity.created(URI.create("/api/v1/orgs/" + orgId + "/projects/" + projectId
                + "/tasks/" + view.task().getId())).body(TaskResponse.from(view));
    }

    @GetMapping
    PageResponse<TaskResponse> list(@PathVariable UUID orgId, @PathVariable UUID projectId,
                                    @RequestParam(name = "status", required = false) TaskStatus status,
                                    @RequestParam(name = "assignee", required = false) UUID assigneeId,
                                    @RequestParam(name = "milestone", required = false) UUID milestoneId,
                                    Pageable pageable,
                                    @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requireMembership(orgId, projectId, user.userId());
        Pageable safe = Pageables.restrictSort(pageable, SORT_WHITELIST, DEFAULT_SORT);
        var page = taskService.list(scope, status, assigneeId, milestoneId, safe);
        return PageResponse.of(page, TaskResponse::from);
    }

    @GetMapping("/{taskId}")
    TaskResponse get(@PathVariable UUID orgId, @PathVariable UUID projectId, @PathVariable UUID taskId,
                     @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requireMembership(orgId, projectId, user.userId());
        return TaskResponse.from(taskService.get(scope, taskId));
    }

    @PatchMapping("/{taskId}")
    TaskResponse update(@PathVariable UUID orgId, @PathVariable UUID projectId, @PathVariable UUID taskId,
                        @Valid @RequestBody UpdateTaskRequest request,
                        @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.TASK_EDIT);
        return TaskResponse.from(taskService.update(scope, taskId, request.title(), request.description(),
                request.priority(), request.assigneeId(), request.milestoneId(), request.dueDate(),
                request.estimatePoints(),
                Boolean.TRUE.equals(request.clearAssignee()),
                Boolean.TRUE.equals(request.clearMilestone()),
                Boolean.TRUE.equals(request.clearDueDate()),
                request.version()));
    }

    @PostMapping("/{taskId}/status")
    TaskResponse changeStatus(@PathVariable UUID orgId, @PathVariable UUID projectId,
                              @PathVariable UUID taskId, @Valid @RequestBody ChangeStatusRequest request,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.TASK_EDIT);
        return TaskResponse.from(taskService.changeStatus(scope, taskId, request.status(), request.version()));
    }

    @DeleteMapping("/{taskId}")
    ResponseEntity<Void> delete(@PathVariable UUID orgId, @PathVariable UUID projectId,
                                @PathVariable UUID taskId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.TASK_EDIT);
        taskService.delete(scope, taskId);
        return ResponseEntity.noContent().build();
    }
}
