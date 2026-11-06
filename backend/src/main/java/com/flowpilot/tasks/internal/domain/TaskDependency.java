package com.flowpilot.tasks.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * "Task A depends on task B": B must be finished before A can start. The composite PK
 * {@code (task_id, depends_on_task_id)} means A depends on B at most once. Self-loops are rejected
 * by a DB CHECK; longer cycles are rejected in {@code DependencyService} with a DFS.
 *
 * <p>org_id is denormalized for the composite FK {@code (task_id, org_id) -> task(id, org_id)}.
 */
@Entity
@Table(name = "task_dependency")
@IdClass(TaskDependencyId.class)
public class TaskDependency {

    @Id
    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Id
    @Column(name = "depends_on_task_id", nullable = false)
    private UUID dependsOnTaskId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TaskDependency() {
    }

    public TaskDependency(UUID taskId, UUID dependsOnTaskId, UUID orgId, UUID projectId, Instant createdAt) {
        this.taskId = taskId;
        this.dependsOnTaskId = dependsOnTaskId;
        this.orgId = orgId;
        this.projectId = projectId;
        this.createdAt = createdAt;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getDependsOnTaskId() {
        return dependsOnTaskId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
