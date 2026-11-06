package com.flowpilot.tasks.internal.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.flowpilot.tasks.internal.application.TaskView;
import com.flowpilot.tasks.internal.domain.Task;
import com.flowpilot.tasks.internal.domain.TaskPriority;
import com.flowpilot.tasks.internal.domain.TaskStatus;

/** Request/response bodies for the task and dependency endpoints. Records: the API contract. */
final class TaskDtos {

    private TaskDtos() {
    }

    record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            TaskPriority priority,
            UUID assigneeId,
            UUID milestoneId,
            LocalDate dueDate,
            @PositiveOrZero @Max(1000) Integer estimatePoints) {
    }

    /** Null fields are left unchanged; version must match (optimistic locking). Status is not here. */
    record UpdateTaskRequest(
            @Size(min = 1, max = 200) String title,
            @Size(max = 5000) String description,
            TaskPriority priority,
            UUID assigneeId,
            UUID milestoneId,
            LocalDate dueDate,
            @PositiveOrZero @Max(1000) Integer estimatePoints,
            @NotNull Long version) {
    }

    record ChangeStatusRequest(@NotNull TaskStatus status, @NotNull Long version) {
    }

    record TaskResponse(UUID id, UUID projectId, int number, String key, String title, String description,
                         TaskStatus status, TaskPriority priority, UUID assigneeId, UUID reporterId,
                         UUID milestoneId, LocalDate dueDate, Integer estimatePoints, Instant startedAt,
                         Instant completedAt, boolean blocked, boolean overdue, Instant createdAt,
                         Instant updatedAt, long version) {

        static TaskResponse from(TaskView view) {
            Task t = view.task();
            return new TaskResponse(t.getId(), t.getProjectId(), t.getNumber(), view.key(), t.getTitle(),
                    t.getDescription(), t.getStatus(), t.getPriority(), t.getAssigneeId(), t.getReporterId(),
                    t.getMilestoneId(), t.getDueDate(), t.getEstimatePoints(), t.getStartedAt(),
                    t.getCompletedAt(), view.blocked(), view.overdue(), t.getCreatedAt(), t.getUpdatedAt(),
                    t.getVersion());
        }
    }
}
