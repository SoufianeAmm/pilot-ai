package com.flowpilot.organizations.internal.application;

import static com.flowpilot.organizations.OrgRole.ADMIN;
import static com.flowpilot.organizations.OrgRole.MEMBER;
import static com.flowpilot.organizations.OrgRole.OWNER;
import static com.flowpilot.organizations.OrgRole.PROJECT_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.flowpilot.organizations.OrgRole;
import com.flowpilot.organizations.internal.persistence.MembershipRepository;
import com.flowpilot.shared.web.ApiException;

class RolePolicyTest {

    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final RolePolicy policy = new RolePolicy(memberships);

    /** actor, target's current role ("NONE" = new invitation), new role, allowed? */
    @ParameterizedTest(name = "{0} sets {1} -> {2}: {3}")
    @CsvSource({
            "OWNER,  OWNER,  MEMBER,          true",
            "OWNER,  NONE,   OWNER,           true",
            "ADMIN,  MEMBER, ADMIN,           true",
            "ADMIN,  NONE,   PROJECT_MANAGER, true",
            "ADMIN,  ADMIN,  MEMBER,          true",
            "ADMIN,  NONE,   OWNER,           false",
            "ADMIN,  MEMBER, OWNER,           false",
            "ADMIN,  OWNER,  MEMBER,          false",
            "PROJECT_MANAGER, MEMBER, MEMBER, false",
            "MEMBER, NONE,   MEMBER,          false"
    })
    void canAssign(OrgRole actor, String current, OrgRole newRole, boolean allowed) {
        OrgRole currentRole = "NONE".equals(current) ? null : OrgRole.valueOf(current);

        assertThat(policy.canAssign(actor, currentRole, newRole)).isEqualTo(allowed);
    }

    @Test
    void canRemove_ownerMayRemoveAnyone_adminEveryoneButOwners_othersNobody() {
        assertThat(policy.canRemove(OWNER, OWNER)).isTrue();
        assertThat(policy.canRemove(ADMIN, ADMIN)).isTrue();
        assertThat(policy.canRemove(ADMIN, MEMBER)).isTrue();
        assertThat(policy.canRemove(ADMIN, OWNER)).isFalse();
        assertThat(policy.canRemove(PROJECT_MANAGER, MEMBER)).isFalse();
        assertThat(policy.canRemove(MEMBER, MEMBER)).isFalse();
    }

    @Test
    void ensureOwnerRemains_lastOwner_isRejectedWith409() {
        UUID orgId = UUID.randomUUID();
        when(memberships.countByOrgIdAndRole(orgId, OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> policy.ensureOwnerRemains(orgId))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status().value()).isEqualTo(409));
    }

    @Test
    void ensureOwnerRemains_anotherOwnerExists_passes() {
        UUID orgId = UUID.randomUUID();
        when(memberships.countByOrgIdAndRole(orgId, OWNER)).thenReturn(2L);

        assertThatCode(() -> policy.ensureOwnerRemains(orgId)).doesNotThrowAnyException();
    }
}
