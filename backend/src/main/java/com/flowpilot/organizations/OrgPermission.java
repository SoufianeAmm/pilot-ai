package com.flowpilot.organizations;

/** Organization-level permissions. Which role has which is defined in {@link OrgRole#permissions()}. */
public enum OrgPermission {
    ORG_UPDATE,
    ORG_DELETE,
    MEMBER_INVITE,
    MEMBER_MANAGE,
    PROJECT_CREATE,
    /** Implicit MANAGER role on every project of the organization (used by the projects module). */
    PROJECT_MANAGE_ALL,
    AUDIT_VIEW
}
