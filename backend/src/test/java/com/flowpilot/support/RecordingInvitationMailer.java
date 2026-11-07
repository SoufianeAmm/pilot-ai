package com.flowpilot.support;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.flowpilot.organizations.internal.application.InvitationMailer;

/** Captures invitation links instead of logging them, so tests can "click" the link. */
public class RecordingInvitationMailer implements InvitationMailer {

    private final Map<String, URI> lastLinkByEmail = new ConcurrentHashMap<>();

    @Override
    public void send(String email, String organizationName, URI acceptLink) {
        lastLinkByEmail.put(email, acceptLink);
    }

    /** The raw token from the most recent invitation sent to this address. */
    public String tokenFor(String email) {
        URI link = lastLinkByEmail.get(email);
        if (link == null) {
            throw new AssertionError("no invitation was sent to " + email);
        }
        return link.getQuery().substring("token=".length());
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        RecordingInvitationMailer recordingInvitationMailer() {
            return new RecordingInvitationMailer();
        }
    }
}
