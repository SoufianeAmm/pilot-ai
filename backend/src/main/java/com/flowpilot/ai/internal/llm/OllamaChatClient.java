package com.flowpilot.ai.internal.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.flowpilot.ai.internal.AiProperties;

/** Calls Ollama's /api/chat endpoint (no streaming, "thinking" mode off). */
@Component
class OllamaChatClient implements ChatClient {

    private final RestClient http;
    private final String model;

    OllamaChatClient(AiProperties properties) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(300));
        this.http = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
        this.model = properties.chatModel();
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public Reply chat(List<Message> messages) {
        List<Map<String, String>> wire = messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList();
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "think", false,
                "keep_alive", "30m",
                "messages", wire,
                "options", Map.of("temperature", 0.2, "num_predict", 400));

        Map<String, Object> response = http.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        if (response == null || !(response.get("message") instanceof Map<?, ?> message)
                || !(message.get("content") instanceof String content)) {
            throw new IllegalStateException("Unexpected response from the chat model.");
        }
        return new Reply(content, asInt(response.get("prompt_eval_count")), asInt(response.get("eval_count")));
    }

    private static Integer asInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}