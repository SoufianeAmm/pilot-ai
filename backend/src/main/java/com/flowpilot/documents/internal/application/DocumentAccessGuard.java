package com.flowpilot.documents.internal.application;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DocumentAccessGuard {

    public void requireRead(UUID userId, UUID orgId, UUID projectId) {
        // intentionally empty until ProjectAccess exists
    }

    public void requireWrite(UUID userId, UUID orgId, UUID projectId) {
        // intentionally empty until ProjectAccess exists
    }
}