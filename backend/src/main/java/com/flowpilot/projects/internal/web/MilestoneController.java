package com.flowpilot.projects.internal.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.projects.ProjectAccess;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.internal.application.MilestoneService;
import com.flowpilot.projects.internal.web.ProjectDtos.CreateMilestoneRequest;
import com.flowpilot.projects.internal.web.ProjectDtos.MilestoneResponse;
import com.flowpilot.projects.internal.web.ProjectDtos.MilestoneStateChangeRequest;
import com.flowpilot.projects.internal.web.ProjectDtos.UpdateMilestoneRequest;
import com.flowpilot.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects/{projectId}/milestones")
class MilestoneController {

    private final MilestoneService milestones;
    private final ProjectAccess projectAccess;

    MilestoneController(MilestoneService milestones, ProjectAccess projectAccess) {
        this.milestones = milestones;
        this.projectAccess = projectAccess;
    }

    @PostMapping
    MilestoneResponse create(@PathVariable UUID orgId, @PathVariable UUID projectId,
                             @Valid @RequestBody CreateMilestoneRequest request,
                             @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.MILESTONE_MANAGE);
        return MilestoneResponse.from(milestones.create(scope, request.name(), request.dueDate()));
    }

    @GetMapping
    List<MilestoneResponse> list(@PathVariable UUID orgId, @PathVariable UUID projectId,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requireMembership(orgId, projectId, user.userId());
        return milestones.list(scope).stream().map(MilestoneResponse::from).toList();
    }

    @GetMapping("/{milestoneId}")
    MilestoneResponse get(@PathVariable UUID orgId, @PathVariable UUID projectId,
                          @PathVariable UUID milestoneId,
                          @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requireMembership(orgId, projectId, user.userId());
        return MilestoneResponse.from(milestones.get(scope, milestoneId));
    }

    @PatchMapping("/{milestoneId}")
    MilestoneResponse update(@PathVariable UUID orgId, @PathVariable UUID projectId,
                             @PathVariable UUID milestoneId,
                             @Valid @RequestBody UpdateMilestoneRequest request,
                             @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.MILESTONE_MANAGE);
        return MilestoneResponse.from(milestones.update(scope, milestoneId, request.name(), request.dueDate(),
                request.version()));
    }

    @PostMapping("/{milestoneId}/complete")
    MilestoneResponse complete(@PathVariable UUID orgId, @PathVariable UUID projectId,
                               @PathVariable UUID milestoneId,
                               @Valid @RequestBody MilestoneStateChangeRequest request,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.MILESTONE_MANAGE);
        return MilestoneResponse.from(milestones.complete(scope, milestoneId, request.version()));
    }

    @PostMapping("/{milestoneId}/reopen")
    MilestoneResponse reopen(@PathVariable UUID orgId, @PathVariable UUID projectId,
                             @PathVariable UUID milestoneId,
                             @Valid @RequestBody MilestoneStateChangeRequest request,
                             @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.MILESTONE_MANAGE);
        return MilestoneResponse.from(milestones.reopen(scope, milestoneId, request.version()));
    }
}
