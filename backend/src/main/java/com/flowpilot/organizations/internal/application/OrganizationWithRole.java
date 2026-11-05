package com.flowpilot.organizations.internal.application;

import com.flowpilot.organizations.OrgRole;
import com.flowpilot.organizations.internal.domain.Organization;

/** An organization together with the calling user's role in it. */
public record OrganizationWithRole(Organization organization, OrgRole role) {
}
