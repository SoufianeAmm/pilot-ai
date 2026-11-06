package com.flowpilot.projects.internal.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.projects.ProjectQueries;
import com.flowpilot.projects.internal.domain.Project;
import com.flowpilot.projects.internal.persistence.MilestoneRepository;
import com.flowpilot.projects.internal.persistence.ProjectRepository;
import com.flowpilot.shared.web.ApiException;

/**
 * Implements {@link ProjectQueries}: the per-project task-number reservation (row-locked, no gaps),
 * the project-key lookup, and milestone-membership validation. Other modules (e.g. {@code tasks})
 * call these instead of touching the {@code Project}/{@code Milestone} entities or repositories,
 * which are private to this module.
 */
@Service
class ProjectQueriesService implements ProjectQueries {

    private final ProjectRepository projects;
    private final MilestoneRepository milestones;

    ProjectQueriesService(ProjectRepository projects, MilestoneRepository milestones) {
        this.projects = projects;
        this.milestones = milestones;
    }

    @Override
    @Transactional
    public int reserveNextTaskNumber(UUID orgId, UUID projectId) {
        Project project = projects.findByIdAndOrgIdForUpdate(projectId, orgId)
                .orElseThrow(() -> ApiException.notFound("Project not found."));
        int number = project.reserveNextTaskNumber();
        projects.flush();
        return number;
    }

    @Override
    @Transactional(readOnly = true)
    public String keyOf(UUID orgId, UUID projectId) {
        return projects.findByIdAndOrgId(projectId, orgId)
                .map(Project::getKey)
                .orElseThrow(() -> ApiException.notFound("Project not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean milestoneExists(UUID orgId, UUID projectId, UUID milestoneId) {
        // Scoped by projectId AND orgId: a task in project A cannot use a milestone from project B.
        return milestones.findByIdAndProjectIdAndOrgId(milestoneId, projectId, orgId).isPresent();
    }
}
