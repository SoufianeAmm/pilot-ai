package com.flowpilot.tasks.internal.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowpilot.tasks.internal.domain.Task;
import com.flowpilot.tasks.internal.domain.TaskStatus;

/**
 * Task data access. Lookups are scoped by {@code projectId} <em>and</em> {@code orgId} (never orgId
 * alone), so a member of one project cannot read or change a task of another project in the same
 * organization. The list query takes nullable filters (status, assignee, milestone); a null filter
 * means "do not filter on this field". {@code findBlockedTaskIds} computes the derived "blocked" flag
 * for a batch of tasks in one query (no N+1) and ignores soft-deleted blockers.
 */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Project- and tenant-scoped, non-deleted lookup. Use this, not the org-only lookup. */
    Optional<Task> findByIdAndProjectIdAndOrgIdAndDeletedAtIsNull(UUID id, UUID projectId, UUID orgId);

    /**
     * Paginated task list with optional filters. Each {@code :param is null or ...} clause is a
     * no-op when the filter is null, so callers pass null for "don't filter on this field".
     */
    @Query("select t from Task t where t.projectId = :projectId and t.orgId = :orgId "
            + "and t.deletedAt is null "
            + "and (:status is null or t.status = :status) "
            + "and (:assigneeId is null or t.assigneeId = :assigneeId) "
            + "and (:milestoneId is null or t.milestoneId = :milestoneId)")
    Page<Task> findForList(@Param("orgId") UUID orgId, @Param("projectId") UUID projectId,
                           @Param("status") TaskStatus status, @Param("assigneeId") UUID assigneeId,
                           @Param("milestoneId") UUID milestoneId, Pageable pageable);

    /**
     * The ids of tasks (among the given batch) that are blocked: they have at least one dependency
     * whose depends-on task is not terminal. One query for the whole batch — the derived "blocked"
     * flag, computed without N+1.
     */
    @Query("select distinct d.taskId from TaskDependency d, Task dep "
            + "where dep.id = d.dependsOnTaskId and d.taskId in :taskIds "
            + "and dep.deletedAt is null "
            + "and dep.status not in :terminalStatuses")
    Set<UUID> findBlockedTaskIds(@Param("taskIds") Collection<UUID> taskIds,
                                 @Param("terminalStatuses") Collection<TaskStatus> terminalStatuses);
}
