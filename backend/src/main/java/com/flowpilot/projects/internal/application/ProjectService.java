package com.flowpilot.projects.internal.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.organizations.OrgPermission;
import com.flowpilot.organizations.OrgScope;
import com.flowpilot.projects.ProjectEvents.ProjectArchivedEvent;
import com.flowpilot.projects.ProjectEvents.ProjectCreatedEvent;
import com.flowpilot.projects.ProjectRole;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.projects.internal.domain.Project;
import com.flowpilot.projects.internal.domain.ProjectMember;
import com.flowpilot.projects.internal.domain.ProjectStatus;
import com.flowpilot.projects.internal.persistence.ProjectMemberRepository;
import com.flowpilot.projects.internal.persistence.ProjectRepository;
import com.flowpilot.shared.id.Ids;
import com.flowpilot.shared.web.ApiException;

/**
 * Project create/read/update. Deletion is "archive" (status -> ARCHIVED) rather than a row delete,
 * so historical tasks and analytics remain valid. Optimistic locking: a stale {@code version} yields
 * 409 with the current representation.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, AuditTrail audit,
                          ApplicationEventPublisher events, Clock clock) {
        this.projects = projects;
        this.members = members;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Caller must already hold {@code PROJECT_CREATE} (an organization-level permission). */
    @Transactional
    public Project create(OrgScope scope, String key, String name, String description,
                          LocalDate startDate, LocalDate targetDate) {
        requireValidDates(startDate, targetDate);
        // Locale.ROOT: with a Turkish default locale "fi".toUpperCase() would give "Fİ", which the key check rejects.
        String normalizedKey = key.toUpperCase(Locale.ROOT);
        if (projects.existsByOrgIdAndKey(scope.orgId(), normalizedKey)) {
            throw duplicateKey();
        }
        Project project = new Project(Ids.newId(), scope.orgId(), normalizedKey, name, description,
                ProjectStatus.PLANNED, startDate, targetDate, scope.userId(), clock.instant());
        try {
            projects.saveAndFlush(project);
        } catch (DataIntegrityViolationException ex) {
            // Only reachable when two requests create the same key at the same moment.
            throw duplicateKey();
        }
        // Org OWNER/ADMIN already manage every project. A PROJECT_MANAGER does not, so without this
        // row they could create a project and then not be able to open it.
        if (!scope.has(OrgPermission.PROJECT_MANAGE_ALL)) {
            members.saveAndFlush(new ProjectMember(project.getId(), scope.userId(), scope.orgId(),
                    ProjectRole.MANAGER, 0, clock.instant()));
        }
        audit.record(AuditEvent.success(AuditActions.PROJECT_CREATED, scope.orgId(), scope.userId())
                .withTarget("project", project.getId()));
        events.publishEvent(new ProjectCreatedEvent(scope.orgId(), project.getId(), scope.userId()));
        return project;
    }

    @Transactional(readOnly = true)
    /**
     * Org OWNER/ADMIN see every project; everyone else sees only the projects they are a member of,
     * the same rule {@code ProjectAccess} applies when a single project is opened.
     */
    public Page<Project> list(OrgScope scope, ProjectStatus statusFilter, Pageable pageable) {
        if (scope.has(OrgPermission.PROJECT_MANAGE_ALL)) {
            return statusFilter == null
                    ? projects.findAllByOrgId(scope.orgId(), pageable)
                    : projects.findAllByOrgIdAndStatus(scope.orgId(), statusFilter, pageable);
        }
        return statusFilter == null
                ? projects.findAllByOrgIdForMember(scope.orgId(), scope.userId(), pageable)
                : projects.findAllByOrgIdAndStatusForMember(scope.orgId(), scope.userId(), statusFilter, pageable);
    }

    @Transactional(readOnly = true)
    public Project get(ProjectScope scope) {
        return load(scope);
    }

    @Transactional
    public Project update(ProjectScope scope, String name, String description, ProjectStatus status,
                          LocalDate startDate, LocalDate targetDate, long expectedVersion) {
        Project project = load(scope);
        if (!Objects.equals(project.getVersion(), expectedVersion)) {
            throw ApiException.conflict("The project was changed by someone else. Reload and try again.");
        }
        requireValidDates(startDate != null ? startDate : project.getStartDate(),
                targetDate != null ? targetDate : project.getTargetDate());
        boolean archiving = status == ProjectStatus.ARCHIVED && project.getStatus() != ProjectStatus.ARCHIVED;
        project.update(name, description, status, startDate, targetDate, clock.instant());
        projects.flush();
        audit.record(AuditEvent.success(AuditActions.PROJECT_UPDATED, scope.orgId(), scope.userId())
                .withTarget("project", scope.projectId()));
        if (archiving) {
            events.publishEvent(new ProjectArchivedEvent(scope.orgId(), scope.projectId(), scope.userId()));
        }
        return project;
    }

    private static void requireValidDates(LocalDate startDate, LocalDate targetDate) {
        if (startDate != null && targetDate != null && targetDate.isBefore(startDate)) {
            throw ApiException.badRequest("The target date cannot be before the start date.");
        }
    }

    private static ApiException duplicateKey() {
        return ApiException.conflict("A project with this key already exists in the organization.");
    }

    private Project load(ProjectScope scope) {
        return projects.findByIdAndOrgId(scope.projectId(), scope.orgId())
                .orElseThrow(() -> ApiException.notFound("Project not found."));
    }
}
