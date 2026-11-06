package com.flowpilot.documents;

import java.util.List;
import java.util.UUID;

/** What other modules (e.g. the AI assistant) may ask the documents module. */
public interface KnowledgeApi {

    /** The passages of the project's documents closest to the question, best first. */
    List<KnowledgePassage> search(UUID projectId, String question, int limit);
}