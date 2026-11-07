package com.flowpilot.organizations.internal.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.flowpilot.organizations.OrgPermission;
import com.flowpilot.organizations.OrgRole;
import com.flowpilot.organizations.internal.persistence.MembershipRepository;
import com.flowpilot.shared.web.ApiException;

/**
 * Who may give which role to whom. Owners may do anything. Admins may manage everyone except
 * owners, and never grant a role above their own. Every organization keeps at least one owner.
 */
@Component
public class RolePolicy {

    private final MembershipRepository memberships;

    public RolePolicy(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    /** @param currentTarget the target's current role, or null when inviting a new member */
    public boolean canAssign(OrgRole actor, OrgRole currentTarget, OrgRole newRole) {
        if (actor == OrgRole.OWNER) {
            return true;
        }
        if (!actor.has(OrgPermission.MEMBER_MANAGE) && !actor.has(OrgPermission.MEMBER_INVITE)) {
            return false;
        }
        return !newRole.outranks(actor) && currentTarget != OrgRole.OWNER;
    }

    public boolean canRemove(OrgRole actor, OrgRole target) {
        if (actor == OrgRole.OWNER) {
            return true;
        }
        return actor.has(OrgPermission.MEMBER_MANAGE) && target != OrgRole.OWNER;
    }

    /**
     * Call before an owner is demoted or removed, while holding the organization row lock.
     *
     * @throws ApiException 409 if this is the organization's last owner
     */
    public void ensureOwnerRemains(UUID orgId) {
        if (memberships.countByOrgIdAndRole(orgId, OrgRole.OWNER) <= 1) {
            throw ApiException.conflict("An organization must keep at least one owner. "
                    + "Make another member an owner first.");
        }
    }
}
