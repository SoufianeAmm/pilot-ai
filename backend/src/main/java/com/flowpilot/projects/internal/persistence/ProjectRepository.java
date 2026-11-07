package com.flowpilot.projects.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowpilot.projects.internal.domain.Project;
import com.flowpilot.projects.internal.domain.ProjectStatus;

/**
 * Project data access. Lookups are always scoped by {@code orgId} so a tenant can never read another
 * tenant's project: there is no {@code findById(UUID)} here (that would bypass tenant isolation).
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /** Tenant-scoped lookup: returns empty if the project does not exist in this organization. */
    Optional<Project> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByOrgIdAndKey(UUID orgId, String key);

    /** Projects of an organization that the user is an explicit member of (project list for non-admins). */
    @Query("""
            select p from Project p
            where p.orgId = :orgId
              and exists (select 1 from ProjectMember m where m.projectId = p.id and m.userId = :userId)
            """)
    Page<Project> findAllByOrgIdForMember(@Param("orgId") UUID orgId, @Param("userId") UUID userId,
                                          Pageable pageable);

    @Query("""
            select p from Project p
            where p.orgId = :orgId and p.status = :status
              and exists (select 1 from ProjectMember m where m.projectId = p.id and m.userId = :userId)
            """)
    Page<Project> findAllByOrgIdAndStatusForMember(@Param("orgId") UUID orgId, @Param("userId") UUID userId,
                                                   @Param("status") ProjectStatus status, Pageable pageable);

    /** All projects of an organization, paginated (the project list for org OWNER/ADMIN). */
    Page<Project> findAllByOrgId(UUID orgId, Pageable pageable);

    Page<Project> findAllByOrgIdAndStatus(UUID orgId,
                                          ProjectStatus status,
                                          Pageable pageable);

    /**
     * Row lock on the project. Used to serialize the per-project task counter
     * ({@code next_task_number}) and milestone numbering, so two concurrent creations never get the
     * same number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id and p.orgId = :orgId")
    Optional<Project> findByIdAndOrgIdForUpdate(@Param("id") UUID id, @Param("orgId") UUID orgId);
}
