package com.flowpilot.ai.internal.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class AiAccessGuard {

    public void requireRead(UUID userId, UUID orgId, UUID projectId) {
        // intentionally empty until ProjectAccess exists
    }

    public void requireWrite(UUID userId, UUID orgId, UUID projectId) {
        // intentionally empty until ProjectAccess exists
    }
}