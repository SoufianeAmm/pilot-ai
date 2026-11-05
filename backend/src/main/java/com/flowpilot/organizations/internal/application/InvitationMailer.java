package com.flowpilot.organizations.internal.application;

import java.net.URI;

/** Delivers invitation links. Replace {@link LoggingInvitationMailer} with an SMTP implementation for real use. */
public interface InvitationMailer {

    void send(String email, String organizationName, URI acceptLink);
}
