package com.flowpilot.shared.web;

import java.net.URI;

/**
 * Stable RFC 9457 problem "type" identifiers. Clients may branch on these values,
 * so they must never change once published.
 */
public final class ProblemTypes {

    public static final URI VALIDATION = URI.create("urn:flowpilot:problem:validation");
    public static final URI UNAUTHORIZED = URI.create("urn:flowpilot:problem:unauthorized");
    public static final URI FORBIDDEN = URI.create("urn:flowpilot:problem:forbidden");
    public static final URI NOT_FOUND = URI.create("urn:flowpilot:problem:not-found");
    public static final URI CONFLICT = URI.create("urn:flowpilot:problem:conflict");
    public static final URI RATE_LIMITED = URI.create("urn:flowpilot:problem:rate-limited");
    public static final URI INTERNAL = URI.create("urn:flowpilot:problem:internal");

    private ProblemTypes() {
    }
}
