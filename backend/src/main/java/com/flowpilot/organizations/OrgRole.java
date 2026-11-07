package com.flowpilot.organizations;

import static com.flowpilot.organizations.OrgPermission.AUDIT_VIEW;
import static com.flowpilot.organizations.OrgPermission.MEMBER_INVITE;
import static com.flowpilot.organizations.OrgPermission.MEMBER_MANAGE;
import static com.flowpilot.organizations.OrgPermission.ORG_UPDATE;
import static com.flowpilot.organizations.OrgPermission.PROJECT_CREATE;
import static com.flowpilot.organizations.OrgPermission.PROJECT_MANAGE_ALL;

import java.util.EnumSet;
import java.util.Set;

/** Organization roles, from most to least powerful. The permission matrix is the one in docs/uml section 21. */
public enum OrgRole {

    OWNER(3, EnumSet.allOf(OrgPermission.class)),
    ADMIN(2, EnumSet.of(ORG_UPDATE, MEMBER_INVITE, MEMBER_MANAGE, PROJECT_CREATE, PROJECT_MANAGE_ALL, AUDIT_VIEW)),
    PROJECT_MANAGER(1, EnumSet.of(PROJECT_CREATE)),
    MEMBER(0, EnumSet.noneOf(OrgPermission.class));

    private final int rank;
    private final Set<OrgPermission> permissions;

    OrgRole(int rank, Set<OrgPermission> permissions) {
        this.rank = rank;
        this.permissions = Set.copyOf(permissions);
    }

    public Set<OrgPermission> permissions() {
        return permissions;
    }

    public boolean has(OrgPermission permission) {
        return permissions.contains(permission);
    }

    public boolean outranks(OrgRole other) {
        return rank > other.rank;
    }
}
