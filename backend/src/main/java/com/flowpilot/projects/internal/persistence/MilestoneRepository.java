package com.flowpilot.projects.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowpilot.projects.internal.domain.Milestone;
import com.flowpilot.projects.internal.domain.MilestoneStatus;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    /**
     * Project-scoped lookup. Scoping by organization alone is not enough: a manager of one project
     * could then read or change the milestones of another project in the same organization.
     */
    Optional<Milestone> findByIdAndProjectIdAndOrgId(UUID id, UUID projectId, UUID orgId);

    List<Milestone> findAllByProjectIdOrderByNumberAsc(UUID projectId);

    List<Milestone> findAllByProjectIdAndStatusOrderByDueDateAsc(UUID projectId, MilestoneStatus status);

    /** Highest milestone number in a project, or null if there are none yet. */
    @Query("select max(m.number) from Milestone m where m.projectId = :projectId")
    Integer findMaxNumberByProjectId(@Param("projectId") UUID projectId);
}
