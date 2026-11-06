package com.flowpilot.tasks.internal.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.projects.ProjectQueries;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.shared.id.Ids;
import com.flowpilot.shared.web.ApiException;
import com.flowpilot.tasks.TaskEvents.TaskCreatedEvent;
import com.flowpilot.tasks.TaskEvents.TaskDeletedEvent;
import com.flowpilot.tasks.TaskEvents.TaskStatusChangedEvent;
import com.flowpilot.tasks.internal.domain.Task;
import com.flowpilot.tasks.internal.domain.TaskPriority;
import com.flowpilot.tasks.internal.domain.TaskStatus;
import com.flowpilot.tasks.internal.persistence.TaskRepository;

/**
 * Task create/read/update/delete. The per-project number (the "n" in "FP-42") is reserved through
 * {@link ProjectQueries#reserveNextTaskNumber}, which row-locks the project so concurrent creates
 * never collide. {@code blocked} and {@code overdue} are derived per read, never stored.
 */
@Service
public class TaskService {

    private static final List<TaskStatus> TERMINAL = List.of(TaskStatus.DONE, TaskStatus.CANCELLED);

    private final TaskRepository tasks;
    private final ProjectQueries projectQueries;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TaskService(TaskRepository tasks, ProjectQueries projectQueries, AuditTrail audit,
                       ApplicationEventPublisher events, Clock clock) {
        this.tasks = tasks;
        this.projectQueries = projectQueries;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public TaskView create(ProjectScope scope, String title, String description, TaskPriority priority,
                           UUID assigneeId, UUID milestoneId, LocalDate dueDate, Integer estimatePoints) {
        int number = projectQueries.reserveNextTaskNumber(scope.orgId(), scope.projectId());
        Task task = new Task(Ids.newId(), scope.orgId(), scope.projectId(), number, title, description,
                priority == null ? TaskPriority.MEDIUM : priority, scope.userId(), assigneeId, milestoneId,
                dueDate, estimatePoints, clock.instant());
        try {
            tasks.saveAndFlush(task);
        } catch (DataIntegrityViolationException ex) {
            // uq_task_project_number should not happen (the row lock serializes numbering), but defend.
            throw ApiException.conflict("The task could not be created due to a conflict.");
        }
        audit.record(AuditEvent.success(AuditActions.TASK_CREATED, scope.orgId(), scope.userId())
                .withTarget("task", task.getId()));
        events.publishEvent(new TaskCreatedEvent(scope.orgId(), scope.projectId(), task.getId(),
                task.getNumber(), scope.userId()));
        return viewOf(scope, task, Set.of());
    }

    @Transactional(readOnly = true)
    public TaskView get(ProjectScope scope, UUID taskId) {
        Task task = load(scope, taskId);
        Set<UUID> blocked = tasks.findBlockedTaskIds(List.of(taskId), TERMINAL);
        return viewOf(scope, task, blocked);
    }

    @Transactional(readOnly = true)
    public Page<TaskView> list(ProjectScope scope, TaskStatus status, UUID assigneeId, UUID milestoneId,
                               Pageable pageable) {
        Page<Task> page = tasks.findForList(scope.orgId(), scope.projectId(), status, assigneeId,
                milestoneId, pageable);
        Set<UUID> blockedIds = page.isEmpty()
                ? Set.of()
                : tasks.findBlockedTaskIds(page.map(Task::getId).toList(), TERMINAL);
        return page.map(t -> viewOf(scope, t, blockedIds));
    }

    /** Update the editable fields (not status — use {@link #changeStatus}). */
    @Transactional
    public TaskView update(ProjectScope scope, UUID taskId, String title, String description,
                           TaskPriority priority, UUID assigneeId, UUID milestoneId, LocalDate dueDate,
                           Integer estimatePoints, long expectedVersion) {
        Task task = load(scope, taskId);
        if (!Objects.equals(task.getVersion(), expectedVersion)) {
            throw ApiException.conflict("The task was changed by someone else. Reload and try again.");
        }
        task.update(title, description, priority, assigneeId, milestoneId, dueDate, estimatePoints,
                clock.instant());
        tasks.flush();
        audit.record(AuditEvent.success(AuditActions.TASK_UPDATED, scope.orgId(), scope.userId())
                .withTarget("task", taskId));
        return viewOf(scope, task, tasks.findBlockedTaskIds(List.of(taskId), TERMINAL));
    }

    @Transactional
    public TaskView changeStatus(ProjectScope scope, UUID taskId, TaskStatus newStatus, long expectedVersion) {
        Task task = load(scope, taskId);
        if (!Objects.equals(task.getVersion(), expectedVersion)) {
            throw ApiException.conflict("The task was changed by someone else. Reload and try again.");
        }
        TaskStatus oldStatus = task.getStatus();
        task.changeStatus(newStatus, clock.instant());
        tasks.flush();
        audit.record(AuditEvent.success(AuditActions.TASK_UPDATED, scope.orgId(), scope.userId())
                .withTarget("task", taskId).withDetail("status", newStatus.name()));
        events.publishEvent(new TaskStatusChangedEvent(scope.orgId(), scope.projectId(), taskId,
                oldStatus.name(), newStatus.name(), scope.userId()));
        return viewOf(scope, task, tasks.findBlockedTaskIds(List.of(taskId), TERMINAL));
    }

    /**
     * Soft-delete. A manager (TASK_MANAGE_ANY) may delete any task; a contributor may delete only
     * tasks they reported.
     */
    @Transactional
    public void delete(ProjectScope scope, UUID taskId) {
        Task task = load(scope, taskId);
        boolean canDeleteAny = scope.has(com.flowpilot.projects.ProjectPermission.TASK_MANAGE_ANY);
        boolean ownTask = task.getReporterId().equals(scope.userId());
        if (!canDeleteAny && !ownTask) {
            throw ApiException.forbidden("You can only delete tasks you reported.");
        }
        task.softDelete(clock.instant());
        tasks.flush();
        audit.record(AuditEvent.success(AuditActions.TASK_DELETED, scope.orgId(), scope.userId())
                .withTarget("task", taskId));
        events.publishEvent(new TaskDeletedEvent(scope.orgId(), scope.projectId(), taskId, scope.userId()));
    }

    private Task load(ProjectScope scope, UUID taskId) {
        return tasks.findByIdAndOrgIdAndDeletedAtIsNull(taskId, scope.orgId())
                .orElseThrow(() -> ApiException.notFound("Task not found."));
    }

    private TaskView viewOf(ProjectScope scope, Task task, Set<UUID> blockedIds) {
        String key = projectQueries.keyOf(scope.orgId(), scope.projectId()) + "-" + task.getNumber();
        boolean blocked = !task.getStatus().isTerminal() && blockedIds.contains(task.getId());
        boolean overdue = task.getDueDate() != null
                && !task.getStatus().isTerminal()
                && task.getDueDate().isBefore(LocalDate.now(clock));
        return new TaskView(task, key, blocked, overdue);
    }
}
