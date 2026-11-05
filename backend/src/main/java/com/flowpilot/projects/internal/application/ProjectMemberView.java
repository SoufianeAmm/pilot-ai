package com.flowpilot.projects.internal.application;

import com.flowpilot.identity.UserSummary;
import com.flowpilot.projects.internal.domain.ProjectMember;

/**
 * A project membership paired with the user's identity, for the member-list response. Built by
 * {@link ProjectMembershipService} from a {@code project_member} row and an {@link IdentityApi} lookup.
 */
public record ProjectMemberView(ProjectMember member, UserSummary user) {
}
