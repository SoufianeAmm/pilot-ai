package com.flowpilot.tasks.internal.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.shared.id.Ids;
import com.flowpilot.shared.web.ApiException;
import com.flowpilot.tasks.TaskEvents.TaskDependencyAddedEvent;
import com.flowpilot.tasks.internal.domain.TaskDependency;
import com.flowpilot.tasks.internal.persistence.TaskDependencyRepository;
import com.flowpilot.tasks.internal.persistence.TaskRepository;

/**
 * Add/remove task dependencies with cycle detection. Adding "A depends on B" is rejected (422) when
 * it would close a cycle; the response carries the cycle path so the client can show it. Self-loops
 * (A depends on A) are rejected with 422 (the DB CHECK is a second line of defense).
 */
@Service
public class DependencyService {

    private final TaskDependencyRepository dependencies;
    private final TaskRepository tasks;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DependencyService(TaskDependencyRepository dependencies, TaskRepository tasks, AuditTrail audit,
                             ApplicationEventPublisher events, Clock clock) {
        this.dependencies = dependencies;
        this.tasks = tasks;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void add(ProjectScope scope, UUID taskId, UUID dependsOnTaskId) {
        if (taskId.equals(dependsOnTaskId)) {
            throw ApiException.unprocessable("A task cannot depend on itself.");
        }
        // both tasks must exist in this project and not be deleted
        loadTask(scope, taskId);
        loadTask(scope, dependsOnTaskId);
        if (dependencies.existsByTaskIdAndDependsOnTaskId(taskId, dependsOnTaskId)) {
            throw ApiException.conflict("This dependency already exists.");
        }
        // cycle check: adding taskId -> dependsOnTaskId creates a cycle iff dependsOnTaskId already
        // transitively depends on taskId. Build the project's graph once and DFS.
        Map<UUID, List<UUID>> graph = dependencies.findAllByProjectId(scope.projectId()).stream()
                .collect(Collectors.groupingBy(TaskDependency::getTaskId,
                        Collectors.mapping(TaskDependency::getDependsOnTaskId, Collectors.toList())));
        List<UUID> cycle = CycleDetector.findCycle(graph, taskId, dependsOnTaskId);
        if (!cycle.isEmpty()) {
            throw ApiException.unprocessable("Adding this dependency would create a cycle: " + pathOf(cycle));
        }
        dependencies.saveAndFlush(new TaskDependency(taskId, dependsOnTaskId, scope.orgId(),
                scope.projectId(), clock.instant()));
        audit.record(AuditEvent.success(AuditActions.TASK_DEPENDENCY_ADDED, scope.orgId(), scope.userId())
                .withTarget("task", taskId).withDetail("dependsOn", dependsOnTaskId.toString()));
        events.publishEvent(new TaskDependencyAddedEvent(scope.orgId(), scope.projectId(), taskId, dependsOnTaskId));
    }

    @Transactional
    public void remove(ProjectScope scope, UUID taskId, UUID dependsOnTaskId) {
        // both tasks must exist in this project (so ids from another tenant are not probed)
        loadTask(scope, taskId);
        loadTask(scope, dependsOnTaskId);
        int deleted = dependencies.deleteByTaskIdAndDependsOnTaskId(taskId, dependsOnTaskId);
        if (deleted == 0) {
            throw ApiException.notFound("Dependency not found.");
        }
        audit.record(AuditEvent.success(AuditActions.TASK_DEPENDENCY_REMOVED, scope.orgId(), scope.userId())
                .withTarget("task", taskId).withDetail("dependsOn", dependsOnTaskId.toString()));
    }

    private void loadTask(ProjectScope scope, UUID taskId) {
        if (tasks.findByIdAndProjectIdAndOrgIdAndDeletedAtIsNull(taskId, scope.projectId(), scope.orgId()).isEmpty()) {
            throw ApiException.notFound("Task not found.");
        }
    }

    private static String pathOf(List<UUID> cycle) {
        return cycle.stream().map(UUID::toString).collect(Collectors.joining(" -> "));
    }
}
