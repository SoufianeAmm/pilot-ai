package com.flowpilot.documents;

import java.util.List;

import com.flowpilot.projects.ProjectScope;

/** What other modules (e.g. the AI assistant) may ask the documents module. */
public interface KnowledgeApi {

    /**
     * The passages of the project's documents closest to the question, best first.
     *
     * <p>Takes a {@link ProjectScope}: the caller must already have passed the project access check,
     * and the search is restricted to that organization and project.
     */
    List<KnowledgePassage> search(ProjectScope scope, String question, int limit);
}
