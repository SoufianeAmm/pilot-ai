package com.flowpilot.organizations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Member lookups for other modules, e.g. the projects module checks that a new project member
 * belongs to the organization. Callers must already hold an OrgScope for orgId.
 */
public interface OrganizationDirectory {

    Optional<MemberSummary> findMember(UUID orgId, UUID userId);

    List<MemberSummary> listMembers(UUID orgId);
}
