package com.flowpilot.documents.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the embedding model runs (flowpilot.ai.ollama.* in application.yml). */
@ConfigurationProperties("flowpilot.ai.ollama")
public record EmbeddingProperties(String baseUrl, String embeddingModel) {
}
