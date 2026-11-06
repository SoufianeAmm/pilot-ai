package com.flowpilot.tasks.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowpilot.tasks.internal.domain.TaskDependency;
import com.flowpilot.tasks.internal.domain.TaskDependencyId;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, TaskDependencyId> {

    /** The dependencies of a task: "task depends on these". */
    List<TaskDependency> findAllByTaskId(UUID taskId);

    /** The tasks that depend on this one: "these are blocked by task" (reverse edges, for cycle DFS). */
    List<TaskDependency> findAllByDependsOnTaskId(UUID dependsOnTaskId);

    boolean existsByTaskIdAndDependsOnTaskId(UUID taskId, UUID dependsOnTaskId);

    /** All dependencies in a project — used to build the graph for cycle detection in one query. */
    List<TaskDependency> findAllByProjectId(UUID projectId);

    @Modifying
    @Query("delete from TaskDependency d where d.taskId = :taskId and d.dependsOnTaskId = :dependsOnTaskId")
    int deleteByTaskIdAndDependsOnTaskId(@Param("taskId") UUID taskId,
                                         @Param("dependsOnTaskId") UUID dependsOnTaskId);
}
