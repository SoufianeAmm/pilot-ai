package com.flowpilot.organizations.internal.application;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development stand-in for e-mail: writes the invitation link to the log so it can be opened by hand.
 * The link contains a secret token, so this must be replaced by a real mailer before any deployment
 * where logs are shared.
 */
@Component
class LoggingInvitationMailer implements InvitationMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingInvitationMailer.class);

    @Override
    public void send(String email, String organizationName, URI acceptLink) {
        log.info("[dev mailer] Invitation to join '{}' for {}: {}", organizationName, email, acceptLink);
    }
}
