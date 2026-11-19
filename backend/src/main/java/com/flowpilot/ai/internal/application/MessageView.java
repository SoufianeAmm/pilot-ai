package com.flowpilot.ai.internal.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.flowpilot.ai.internal.application.EvidenceLedger.Citation;

/**
 * A message as the API returns it.
 *
 * @param content      the question, or the answer's summary (or an error text when status is not OK)
 * @param status       OK, TIMEOUT, ERROR or INVALID_OUTPUT
 * @param answer       the structured answer (assistant messages with status OK only)
 * @param citations    where each cited reference points to (document and pages)
 * @param verification how the answer was checked; null for questions
 */
public record MessageView(UUID id, String role, String content, String status, AnalystResponse answer,
                          List<Citation> citations, Verification verification, String model, Instant createdAt) {

    /**
     * @param sourcesProvided      passages given to the model
     * @param citedRefs            references the answer uses, all verified
     * @param unverifiedReferences references the model invented; they were removed from the answer
     * @param hasEvidence          the answer cites at least one verified source
     * @param repaired             the first reply was invalid and the model was asked once to fix it
     */
    public record Verification(int sourcesProvided, List<String> citedRefs, List<String> unverifiedReferences,
                               boolean hasEvidence, boolean repaired) {
    }

    /** What is stored in ai_message.structured_response for an answered question. */
    record StoredAnswer(AnalystResponse answer, List<Citation> citations) {
    }
}
