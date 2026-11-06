package com.flowpilot.tasks.internal.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A task within a project, numbered "KEY-n" (e.g. "FP-42"). The number is unique per project and
 * assigned by {@code ProjectQueries.reserveNextTaskNumber} under a row lock.
 *
 * <p>Soft-deleted via {@code deleted_at} (queries filter it). Optimistic locking via {@code @Version}
 * → 409 on a stale update. {@code completed_at} is set when the status becomes terminal and cleared
 * when it leaves terminal, to satisfy the DB CHECK constraint.
 */
@Entity
@Table(name = "task")
public class Task {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false, length = 200)
    private String title;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPriority priority;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "milestone_id")
    private UUID milestoneId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "estimate_points")
    private Integer estimatePoints;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Task() {
    }

    public Task(UUID id, UUID orgId, UUID projectId, int number, String title, String description,
                TaskPriority priority, UUID reporterId, UUID assigneeId, UUID milestoneId,
                LocalDate dueDate, Integer estimatePoints, Instant createdAt) {
        this.id = id;
        this.orgId = orgId;
        this.projectId = projectId;
        this.number = number;
        this.title = title;
        this.description = description;
        this.status = TaskStatus.TODO;
        this.priority = priority;
        this.reporterId = reporterId;
        this.assigneeId = assigneeId;
        this.milestoneId = milestoneId;
        this.dueDate = dueDate;
        this.estimatePoints = estimatePoints;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Update the editable fields. Null fields are left unchanged. Status transitions go through
     * {@link #changeStatus} to keep {@code completed_at} consistent with the DB CHECK.
     */
    public void update(String title, String description, TaskPriority priority, UUID assigneeId,
                       UUID milestoneId, LocalDate dueDate, Integer estimatePoints, Instant now) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (priority != null) {
            this.priority = priority;
        }
        if (assigneeId != null) {
            this.assigneeId = assigneeId;
        }
        if (milestoneId != null) {
            this.milestoneId = milestoneId;
        }
        if (dueDate != null) {
            this.dueDate = dueDate;
        }
        if (estimatePoints != null) {
            this.estimatePoints = estimatePoints;
        }
        this.updatedAt = now;
    }

    /** Transition the status, maintaining the completed_at/started_at invariants. */
    public void changeStatus(TaskStatus newStatus, Instant now) {
        if (newStatus == this.status) {
            return;
        }
        if (newStatus.isTerminal()) {
            this.completedAt = now;
            if (this.startedAt == null) {
                this.startedAt = now;
            }
        } else {
            this.completedAt = null;
            if (newStatus == TaskStatus.IN_PROGRESS && this.startedAt == null) {
                this.startedAt = now;
            }
        }
        this.status = newStatus;
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public UUID getMilestoneId() {
        return milestoneId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Integer getEstimatePoints() {
        return estimatePoints;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Task that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
