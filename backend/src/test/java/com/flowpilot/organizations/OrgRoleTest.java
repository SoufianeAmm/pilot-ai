package com.flowpilot.organizations;

import static com.flowpilot.organizations.OrgPermission.AUDIT_VIEW;
import static com.flowpilot.organizations.OrgPermission.MEMBER_INVITE;
import static com.flowpilot.organizations.OrgPermission.MEMBER_MANAGE;
import static com.flowpilot.organizations.OrgPermission.ORG_DELETE;
import static com.flowpilot.organizations.OrgPermission.ORG_UPDATE;
import static com.flowpilot.organizations.OrgPermission.PROJECT_CREATE;
import static com.flowpilot.organizations.OrgPermission.PROJECT_MANAGE_ALL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

/** Pins the permission matrix documented in docs/uml section 21; a change here must update the document. */
class OrgRoleTest {

    @Test
    void owner_hasEveryPermission() {
        assertThat(OrgRole.OWNER.permissions()).isEqualTo(EnumSet.allOf(OrgPermission.class));
    }

    @Test
    void admin_hasEverythingExceptDeletingTheOrganization() {
        assertThat(OrgRole.ADMIN.permissions()).containsExactlyInAnyOrder(
                ORG_UPDATE, MEMBER_INVITE, MEMBER_MANAGE, PROJECT_CREATE, PROJECT_MANAGE_ALL, AUDIT_VIEW);
        assertThat(OrgRole.ADMIN.has(ORG_DELETE)).isFalse();
    }

    @Test
    void projectManager_canOnlyCreateProjects() {
        assertThat(OrgRole.PROJECT_MANAGER.permissions()).containsExactly(PROJECT_CREATE);
    }

    @Test
    void member_hasNoOrganizationPermissions() {
        assertThat(OrgRole.MEMBER.permissions()).isEmpty();
    }

    @Test
    void outranks_followsOwnerAdminProjectManagerMember() {
        assertThat(OrgRole.OWNER.outranks(OrgRole.ADMIN)).isTrue();
        assertThat(OrgRole.ADMIN.outranks(OrgRole.PROJECT_MANAGER)).isTrue();
        assertThat(OrgRole.PROJECT_MANAGER.outranks(OrgRole.MEMBER)).isTrue();
        assertThat(OrgRole.ADMIN.outranks(OrgRole.ADMIN)).isFalse();
        assertThat(OrgRole.MEMBER.outranks(OrgRole.OWNER)).isFalse();
    }
}
