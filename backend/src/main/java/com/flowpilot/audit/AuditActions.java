package com.flowpilot.audit;

/** Audit action names. Other modules add their own constants here (e.g. PROJECT_DELETED). */
public final class AuditActions {

    public static final String AUTH_REGISTERED = "auth.registered";
    public static final String AUTH_LOGIN_SUCCEEDED = "auth.login.succeeded";
    public static final String AUTH_LOGIN_FAILED = "auth.login.failed";
    public static final String AUTH_LOGIN_RATE_LIMITED = "auth.login.rate_limited";
    public static final String AUTH_REFRESH_REUSE_DETECTED = "auth.refresh.reuse_detected";

    public static final String ORG_CREATED = "org.created";
    public static final String ORG_UPDATED = "org.updated";
    public static final String MEMBER_INVITED = "org.member.invited";
    public static final String INVITATION_REVOKED = "org.invitation.revoked";
    public static final String MEMBER_JOINED = "org.member.joined";
    public static final String MEMBER_ROLE_CHANGED = "org.member.role_changed";
    public static final String MEMBER_REMOVED = "org.member.removed";

    public static final String ACCESS_DENIED = "access.denied";
    public static final String AI_TOOL_DENIED = "ai.tool.denied";

    private AuditActions() {
    }
}
