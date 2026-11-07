package com.flowpilot.projects.internal.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.projects.ProjectAccess;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.internal.application.ProjectMembershipService;
import com.flowpilot.projects.internal.web.ProjectDtos.AddMemberRequest;
import com.flowpilot.projects.internal.web.ProjectDtos.ChangeMemberRoleRequest;
import com.flowpilot.projects.internal.web.ProjectDtos.MemberResponse;
import com.flowpilot.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects/{projectId}/members")
class ProjectMemberController {

    private final ProjectMembershipService memberships;
    private final ProjectAccess projectAccess;

    ProjectMemberController(ProjectMembershipService memberships, ProjectAccess projectAccess) {
        this.memberships = memberships;
        this.projectAccess = projectAccess;
    }

    @PostMapping
    MemberResponse add(@PathVariable UUID orgId, @PathVariable UUID projectId,
                       @Valid @RequestBody AddMemberRequest request,
                       @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.MEMBER_MANAGE);
        int capacity = request.weeklyCapacityPoints() == null ? 0 : request.weeklyCapacityPoints();
        return MemberResponse.from(memberships.add(scope, request.userId(), request.role(), capacity));
    }

    @GetMapping
    List<MemberResponse> list(@PathVariable UUID orgId, @PathVariable UUID projectId,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requireMembership(orgId, projectId, user.userId());
        return memberships.list(scope).stream().map(MemberResponse::from).toList();
    }

    @PatchMapping("/{userId}")
    MemberResponse changeRole(@PathVariable UUID orgId, @PathVariable UUID projectId,
                              @PathVariable UUID userId,
                              @Valid @RequestBody ChangeMemberRoleRequest request,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.MEMBER_MANAGE);
        return MemberResponse.from(memberships.changeRole(scope, userId, request.role(), request.version()));
    }

    @DeleteMapping("/{userId}")
    ResponseEntity<Void> remove(@PathVariable UUID orgId, @PathVariable UUID projectId,
                                @PathVariable UUID userId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.MEMBER_MANAGE);
        memberships.remove(scope, userId);
        return ResponseEntity.noContent().build();
    }
}
