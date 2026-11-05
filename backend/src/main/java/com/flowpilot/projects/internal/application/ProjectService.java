package com.flowpilot.projects.internal.application;

import java.time.Clock;
import java.time.LocalDate;
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
import com.flowpilot.organizations.OrgScope;
import com.flowpilot.projects.ProjectEvents.ProjectArchivedEvent;
import com.flowpilot.projects.ProjectEvents.ProjectCreatedEvent;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.projects.internal.domain.Project;
import com.flowpilot.projects.internal.domain.ProjectStatus;
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
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, AuditTrail audit,
                          ApplicationEventPublisher events, Clock clock) {
        this.projects = projects;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Caller must already hold {@code PROJECT_CREATE} (an organization-level permission). */
    @Transactional
    public Project create(OrgScope scope, String key, String name, String description,
                          LocalDate startDate, LocalDate targetDate) {
        Project project = new Project(Ids.newId(), scope.orgId(), key.toUpperCase(), name, description,
                ProjectStatus.PLANNED, startDate, targetDate, scope.userId(), clock.instant());
        try {
            projects.saveAndFlush(project);
        } catch (DataIntegrityViolationException ex) {
            // uq_project_org_key or ck_project_key violated
            throw ApiException.conflict("A project with this key already exists in the organization.");
        }
        audit.record(AuditEvent.success(AuditActions.PROJECT_CREATED, scope.orgId(), scope.userId())
                .withTarget("project", project.getId()));
        events.publishEvent(new ProjectCreatedEvent(scope.orgId(), project.getId(), scope.userId()));
        return project;
    }

    @Transactional(readOnly = true)
    public Page<Project> list(OrgScope scope, ProjectStatus statusFilter, Pageable pageable) {
        if (statusFilter != null) {
            return projects.findAllByOrgIdAndStatus(scope.orgId(), statusFilter, pageable);
        }
        return projects.findAllByOrgId(scope.orgId(), pageable);
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

    private Project load(ProjectScope scope) {
        return projects.findByIdAndOrgId(scope.projectId(), scope.orgId())
                .orElseThrow(() -> ApiException.notFound("Project not found."));
    }
}
