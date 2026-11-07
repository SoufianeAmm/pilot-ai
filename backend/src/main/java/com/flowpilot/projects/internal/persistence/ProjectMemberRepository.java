package com.flowpilot.projects.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowpilot.projects.internal.domain.ProjectMember;
import com.flowpilot.projects.internal.domain.ProjectMemberId;

/**
 * Project membership data access. The composite id {@code (project_id, user_id)} means JPA operations
 * use {@link ProjectMemberId}.
 */
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectMember> findAllByProjectId(UUID projectId);

    /** All project memberships of a user within one organization (used when they leave the org). */
    List<ProjectMember> findAllByOrgIdAndUserId(UUID orgId, UUID userId);

    @Modifying
    @Query("delete from ProjectMember m where m.orgId = :orgId and m.userId = :userId")
    int deleteByOrgIdAndUserId(@Param("orgId") UUID orgId, @Param("userId") UUID userId);
}
