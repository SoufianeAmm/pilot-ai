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

    /** Tenant-scoped lookup. */
    Optional<Milestone> findByIdAndOrgId(UUID id, UUID orgId);

    List<Milestone> findAllByProjectIdOrderByNumberAsc(UUID projectId);

    List<Milestone> findAllByProjectIdAndStatusOrderByDueDateAsc(UUID projectId, MilestoneStatus status);

    /** Highest milestone number in a project, or null if there are none yet. */
    @Query("select max(m.number) from Milestone m where m.projectId = :projectId")
    Integer findMaxNumberByProjectId(@Param("projectId") UUID projectId);
}
