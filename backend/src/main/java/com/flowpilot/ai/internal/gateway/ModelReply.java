package com.flowpilot.ai.internal.gateway;

/** What one model call produced, with what it cost. Token counts are null if the provider did not report them. */
public record ModelReply(String text, String model, Integer promptTokens, Integer completionTokens, int latencyMs) {
}
