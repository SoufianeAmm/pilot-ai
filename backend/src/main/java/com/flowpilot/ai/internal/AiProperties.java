package com.flowpilot.ai.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the chat model runs (flowpilot.ai.ollama.* in application.yml). */
@ConfigurationProperties("flowpilot.ai.ollama")
public record AiProperties(String baseUrl, String chatModel) {
}
