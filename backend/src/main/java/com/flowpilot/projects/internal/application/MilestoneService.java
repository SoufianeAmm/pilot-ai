package com.flowpilot.projects.internal.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.projects.ProjectEvents.MilestoneCompletedEvent;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.projects.internal.domain.Milestone;
import com.flowpilot.projects.internal.domain.MilestoneStatus;
import com.flowpilot.projects.internal.persistence.MilestoneRepository;
import com.flowpilot.projects.internal.persistence.ProjectRepository;
import com.flowpilot.shared.id.Ids;
import com.flowpilot.shared.web.ApiException;

/**
 * Milestone create/list/complete/reopen. The number "M-n" is assigned under a pessimistic lock on
 * the project row, so two concurrent creates never get the same number (the row lock serializes
 * them, exactly like the task counter will in the tasks module).
 */
@Service
public class MilestoneService {

    private final MilestoneRepository milestones;
    private final ProjectRepository projects;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MilestoneService(MilestoneRepository milestones, ProjectRepository projects,
                            AuditTrail audit, ApplicationEventPublisher events, Clock clock) {
        this.milestones = milestones;
        this.projects = projects;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Milestone create(ProjectScope scope, String name, LocalDate dueDate) {
        // Lock the project row so milestone numbers are assigned sequentially.
        projects.findByIdAndOrgIdForUpdate(scope.projectId(), scope.orgId())
                .orElseThrow(() -> ApiException.notFound("Project not found."));
        Integer max = milestones.findMaxNumberByProjectId(scope.projectId());
        int number = (max == null ? 0 : max) + 1;
        Milestone milestone = new Milestone(Ids.newId(), scope.orgId(), scope.projectId(), number,
                name, dueDate, clock.instant());
        milestones.saveAndFlush(milestone);
        audit.record(AuditEvent.success(AuditActions.MILESTONE_CREATED, scope.orgId(), scope.userId())
                .withTarget("milestone", milestone.getId()));
        return milestone;
    }

    @Transactional(readOnly = true)
    public List<Milestone> list(ProjectScope scope) {
        return milestones.findAllByProjectIdOrderByNumberAsc(scope.projectId());
    }

    @Transactional(readOnly = true)
    public List<Milestone> listOpen(ProjectScope scope) {
        return milestones.findAllByProjectIdAndStatusOrderByDueDateAsc(scope.projectId(), MilestoneStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public Milestone get(ProjectScope scope, UUID milestoneId) {
        return load(scope, milestoneId);
    }

    @Transactional
    public Milestone update(ProjectScope scope, UUID milestoneId, String name, LocalDate dueDate,
                            long expectedVersion) {
        Milestone milestone = load(scope, milestoneId);
        if (!Objects.equals(milestone.getVersion(), expectedVersion)) {
            throw ApiException.conflict("The milestone was changed by someone else. Reload and try again.");
        }
        milestone.update(name, dueDate);
        milestones.flush();
        return milestone;
    }

    @Transactional
    public Milestone complete(ProjectScope scope, UUID milestoneId, long expectedVersion) {
        Milestone milestone = load(scope, milestoneId);
        if (!Objects.equals(milestone.getVersion(), expectedVersion)) {
            throw ApiException.conflict("The milestone was changed by someone else. Reload and try again.");
        }
        milestone.complete();
        milestones.flush();
        audit.record(AuditEvent.success(AuditActions.MILESTONE_COMPLETED, scope.orgId(), scope.userId())
                .withTarget("milestone", milestoneId));
        events.publishEvent(new MilestoneCompletedEvent(scope.orgId(), scope.projectId(),
                milestoneId, milestone.getNumber()));
        return milestone;
    }

    @Transactional
    public Milestone reopen(ProjectScope scope, UUID milestoneId, long expectedVersion) {
        Milestone milestone = load(scope, milestoneId);
        if (!Objects.equals(milestone.getVersion(), expectedVersion)) {
            throw ApiException.conflict("The milestone was changed by someone else. Reload and try again.");
        }
        milestone.reopen();
        milestones.flush();
        return milestone;
    }

    private Milestone load(ProjectScope scope, UUID milestoneId) {
        return milestones.findByIdAndProjectIdAndOrgId(milestoneId, scope.projectId(), scope.orgId())
                .orElseThrow(() -> ApiException.notFound("Milestone not found."));
    }
}
