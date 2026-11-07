package com.flowpilot.documents;

import java.util.UUID;

/** A passage of a document, with what is needed to cite it. */
public record KnowledgePassage(UUID chunkId, UUID documentId, String filename, int chunkIndex,
                               Integer pageFrom, Integer pageTo, String content, double score) {
}
