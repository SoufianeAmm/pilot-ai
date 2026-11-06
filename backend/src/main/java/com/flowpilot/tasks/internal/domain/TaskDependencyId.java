package com.flowpilot.tasks.internal.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key of {@link TaskDependency}: a task depends on exactly one other task at most
 * once. Required by JPA's {@code @IdClass} mapping because {@code task_dependency} has no surrogate id.
 */
public class TaskDependencyId implements Serializable {

    private UUID taskId;
    private UUID dependsOnTaskId;

    public TaskDependencyId() {
    }

    public TaskDependencyId(UUID taskId, UUID dependsOnTaskId) {
        this.taskId = taskId;
        this.dependsOnTaskId = dependsOnTaskId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getDependsOnTaskId() {
        return dependsOnTaskId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TaskDependencyId that)) {
            return false;
        }
        return Objects.equals(taskId, that.taskId) && Objects.equals(dependsOnTaskId, that.dependsOnTaskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, dependsOnTaskId);
    }
}
