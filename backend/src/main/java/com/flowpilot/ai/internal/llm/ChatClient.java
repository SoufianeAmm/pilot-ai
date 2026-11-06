package com.flowpilot.ai.internal.llm;

import java.util.List;

/** Talks to a chat model. An interface, so the provider can be replaced (or faked in tests). */
public interface ChatClient {

    record Message(String role, String content) {
    }

    record Reply(String content, Integer promptTokens, Integer completionTokens) {
    }

    String modelName();

    Reply chat(List<Message> messages);
}