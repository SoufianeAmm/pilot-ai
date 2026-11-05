package com.flowpilot.organizations.internal.application;

import com.flowpilot.organizations.MemberSummary;

/** A member plus the membership version, which clients send back when changing the role (optimistic locking). */
public record MemberView(MemberSummary member, long version) {
}
