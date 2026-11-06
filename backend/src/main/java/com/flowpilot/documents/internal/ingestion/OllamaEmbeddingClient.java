package com.flowpilot.documents.internal.ingestion;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.flowpilot.documents.internal.EmbeddingProperties;

/** Calls Ollama's /api/embed endpoint. */
@Component
class OllamaEmbeddingClient implements EmbeddingClient {

    /** Must match the vector(768) column: nomic-embed-text produces 768 numbers. */
    static final int DIMENSIONS = 768;
    private static final int BATCH_SIZE = 16;

    private final RestClient http;
    private final String model;

    OllamaEmbeddingClient(EmbeddingProperties properties) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.http = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
        this.model = properties.embeddingModel();
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            vectors.addAll(embedBatch(texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()))));
        }
        return vectors;
    }

    private List<float[]> embedBatch(List<String> batch) {
        Map<String, Object> response = http.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "input", batch))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        Object embeddings = response == null ? null : response.get("embeddings");
        if (!(embeddings instanceof List<?> rows) || rows.size() != batch.size()) {
            throw new IllegalStateException("Unexpected response from the embedding service.");
        }
        List<float[]> vectors = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (!(row instanceof List<?> numbers) || numbers.size() != DIMENSIONS) {
                throw new IllegalStateException("The embedding model did not return " + DIMENSIONS + " dimensions.");
            }
            float[] vector = new float[numbers.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = ((Number) numbers.get(i)).floatValue();
            }
            vectors.add(vector);
        }
        return vectors;
    }
}