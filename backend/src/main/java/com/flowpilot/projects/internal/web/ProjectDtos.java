package com.flowpilot.projects.internal.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;

import com.flowpilot.projects.ProjectRole;
import com.flowpilot.projects.internal.application.ProjectMemberView;
import com.flowpilot.projects.internal.domain.Milestone;
import com.flowpilot.projects.internal.domain.MilestoneStatus;
import com.flowpilot.projects.internal.domain.Project;
import com.flowpilot.projects.internal.domain.ProjectStatus;

/**
 * Request and response bodies of the project, milestone and project-member endpoints. All records:
 * the API contract is the record shape, and entities never leave the {@code internal} package.
 */
final class ProjectDtos {

    private ProjectDtos() {
    }

    /**
     * @param key per-org human prefix (e.g. "FP"); stored upper-cased. Accepted in either case so
     *            users may type "fp".
     */
    record CreateProjectRequest(
            @NotBlank @Size(min = 2, max = 10)
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]{1,9}$",
                     message = "must start with a letter and contain only letters and digits (2-10 chars)")
            String key,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            LocalDate startDate,
            LocalDate targetDate) {
    }

    /** Null fields are left unchanged; {@code version} must match the current one (optimistic locking). */
    record UpdateProjectRequest(
            @Size(min = 1, max = 120) String name,
            @Size(max = 2000) String description,
            ProjectStatus status,
            LocalDate startDate,
            LocalDate targetDate,
            @NotNull Long version) {
    }

    record ProjectResponse(UUID id, UUID orgId, String key, String name, String description,
                           ProjectStatus status, LocalDate startDate, LocalDate targetDate,
                           UUID ownerId, int nextTaskNumber, Instant createdAt, Instant updatedAt,
                           long version) {

        static ProjectResponse from(Project p) {
            return new ProjectResponse(p.getId(), p.getOrgId(), p.getKey(), p.getName(), p.getDescription(),
                    p.getStatus(), p.getStartDate(), p.getTargetDate(), p.getOwnerId(), p.getNextTaskNumber(),
                    p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
        }
    }

    record CreateMilestoneRequest(
            @NotBlank @Size(max = 120) String name,
            LocalDate dueDate) {
    }

    record UpdateMilestoneRequest(
            @Size(min = 1, max = 120) String name,
            LocalDate dueDate,
            @NotNull Long version) {
    }

    /** Optimistic-locking version for the complete/reopen state transition. */
    record MilestoneStateChangeRequest(@NotNull Long version) {
    }

    record MilestoneResponse(UUID id, UUID projectId, int number, String name, LocalDate dueDate,
                             MilestoneStatus status, Instant createdAt, long version) {

        static MilestoneResponse from(Milestone m) {
            return new MilestoneResponse(m.getId(), m.getProjectId(), m.getNumber(), m.getName(),
                    m.getDueDate(), m.getStatus(), m.getCreatedAt(), m.getVersion());
        }
    }

    record AddMemberRequest(
            @NotNull UUID userId,
            @NotNull ProjectRole role,
            @PositiveOrZero @Max(100) Integer weeklyCapacityPoints) {
    }

    record ChangeMemberRoleRequest(@NotNull ProjectRole role, @NotNull Long version) {
    }

    record MemberResponse(UUID userId, String displayName, String email, ProjectRole role,
                           int weeklyCapacityPoints, Instant joinedAt, long version) {

        static MemberResponse from(ProjectMemberView view) {
            var member = view.member();
            var user = view.user();
            return new MemberResponse(member.getUserId(), user.displayName(), user.email(), member.getRole(),
                    member.getWeeklyCapacityPoints(), member.getJoinedAt(), member.getVersion());
        }
    }
}
