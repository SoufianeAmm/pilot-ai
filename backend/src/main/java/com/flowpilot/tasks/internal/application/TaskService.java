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
import com.flowpilot.projects.ProjectAccess;
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
 *
 * <p>Lookups are scoped by {@code projectId} <em>and</em> {@code orgId}: a member of one project
 * cannot access a task of another project in the same organization. Assignees are validated to be
 * project members; milestones are validated to belong to the task's project.
 */
@Service
public class TaskService {

    private static final List<TaskStatus> TERMINAL = List.of(TaskStatus.DONE, TaskStatus.CANCELLED);

    private final TaskRepository tasks;
    private final ProjectQueries projectQueries;
    private final ProjectAccess projectAccess;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TaskService(TaskRepository tasks, ProjectQueries projectQueries, ProjectAccess projectAccess,
                       AuditTrail audit, ApplicationEventPublisher events, Clock clock) {
        this.tasks = tasks;
        this.projectQueries = projectQueries;
        this.projectAccess = projectAccess;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public TaskView create(ProjectScope scope, String title, String description, TaskPriority priority,
                           UUID assigneeId, UUID milestoneId, LocalDate dueDate, Integer estimatePoints) {
        validateAssignee(scope, assigneeId);
        validateMilestone(scope, milestoneId);
        int number = projectQueries.reserveNextTaskNumber(scope.orgId(), scope.projectId());
        Task task = new Task(Ids.newId(), scope.orgId(), scope.projectId(), number, title, description,
                priority == null ? TaskPriority.MEDIUM : priority, scope.userId(), assigneeId, milestoneId,
                dueDate, estimatePoints, clock.instant());
        try {
            tasks.saveAndFlush(task);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("The task could not be created due to a conflict.");
        }
        audit.record(AuditEvent.success(AuditActions.TASK_CREATED, scope.orgId(), scope.userId())
                .withTarget("task", task.getId()));
        events.publishEvent(new TaskCreatedEvent(scope.orgId(), scope.projectId(), task.getId(),
                task.getNumber(), scope.userId()));
        String key = projectQueries.keyOf(scope.orgId(), scope.projectId()) + "-" + task.getNumber();
        return new TaskView(task, key, false, isOverdue(task));
    }

    @Transactional(readOnly = true)
    public TaskView get(ProjectScope scope, UUID taskId) {
        Task task = load(scope, taskId);
        String key = projectQueries.keyOf(scope.orgId(), scope.projectId()) + "-" + task.getNumber();
        return new TaskView(task, key, isBlocked(scope, task), isOverdue(task));
    }

    @Transactional(readOnly = true)
    public Page<TaskView> list(ProjectScope scope, TaskStatus status, UUID assigneeId, UUID milestoneId,
                               Pageable pageable) {
        Page<Task> page = tasks.findForList(scope.orgId(), scope.projectId(), status, assigneeId,
                milestoneId, pageable);
        Set<UUID> blockedIds = page.isEmpty()
                ? Set.of()
                : tasks.findBlockedTaskIds(page.map(Task::getId).toList(), TERMINAL);
        String projectKey = projectQueries.keyOf(scope.orgId(), scope.projectId());
        return page.map(t -> new TaskView(t, projectKey + "-" + t.getNumber(),
                blockedIds.contains(t.getId()), isOverdue(t)));
    }

    @Transactional
    public TaskView update(ProjectScope scope, UUID taskId, String title, String description,
                           TaskPriority priority, UUID assigneeId, UUID milestoneId, LocalDate dueDate,
                           Integer estimatePoints, boolean clearAssignee, boolean clearMilestone,
                           boolean clearDueDate, long expectedVersion) {
        Task task = load(scope, taskId);
        if (!Objects.equals(task.getVersion(), expectedVersion)) {
            throw ApiException.conflict("The task was changed by someone else. Reload and try again.");
        }
        if (!clearAssignee) {
            validateAssignee(scope, assigneeId);
        }
        if (!clearMilestone) {
            validateMilestone(scope, milestoneId);
        }
        task.update(title, description, priority, assigneeId, milestoneId, dueDate, estimatePoints,
                clock.instant());
        if (clearAssignee) {
            task.clearAssignee();
        }
        if (clearMilestone) {
            task.clearMilestone();
        }
        if (clearDueDate) {
            task.clearDueDate();
        }
        tasks.flush();
        audit.record(AuditEvent.success(AuditActions.TASK_UPDATED, scope.orgId(), scope.userId())
                .withTarget("task", taskId));
        String key = projectQueries.keyOf(scope.orgId(), scope.projectId()) + "-" + task.getNumber();
        return new TaskView(task, key, isBlocked(scope, task), isOverdue(task));
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
        String key = projectQueries.keyOf(scope.orgId(), scope.projectId()) + "-" + task.getNumber();
        return new TaskView(task, key, isBlocked(scope, task), isOverdue(task));
    }

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
        return tasks.findByIdAndProjectIdAndOrgIdAndDeletedAtIsNull(taskId, scope.projectId(), scope.orgId())
                .orElseThrow(() -> ApiException.notFound("Task not found."));
    }

    private void validateAssignee(ProjectScope scope, UUID assigneeId) {
        if (assigneeId == null) {
            return;
        }
        if (projectAccess.findRole(scope.orgId(), scope.projectId(), assigneeId).isEmpty()) {
            throw ApiException.badRequest("The assignee is not a member of the project.");
        }
    }

    private void validateMilestone(ProjectScope scope, UUID milestoneId) {
        if (milestoneId == null) {
            return;
        }
        if (!projectQueries.milestoneExists(scope.orgId(), scope.projectId(), milestoneId)) {
            throw ApiException.badRequest("The milestone does not belong to this project.");
        }
    }

    private boolean isBlocked(ProjectScope scope, Task task) {
        if (task.getStatus().isTerminal()) {
            return false;
        }
        return tasks.findBlockedTaskIds(List.of(task.getId()), TERMINAL).contains(task.getId());
    }

    private boolean isOverdue(Task task) {
        return task.getDueDate() != null
                && !task.getStatus().isTerminal()
                && task.getDueDate().isBefore(LocalDate.now(clock));
    }
}
