package com.flowpilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.flowpilot.shared.web.RequestIdFilter;
import com.flowpilot.support.PostgresIntegrationTest;

/**
 * Boots the complete application on random ports against a real database and checks it
 * over real HTTP, including the separate actuator (management) port.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class ApplicationIT extends PostgresIntegrationTest {

    @Value("${local.server.port}")
    int serverPort;

    @Value("${local.management.port}")
    int managementPort;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void readinessProbe_isUp() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/health/readiness", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void prometheusMetrics_areExposedOnManagementPortOnly() throws Exception {
        assertThat(get(managementPort, "/actuator/prometheus", null).body()).contains("hikaricp_connections");
        assertThat(get(serverPort, "/actuator/health", null).statusCode()).isEqualTo(404);
    }

    @Test
    void metaEndpoint_returnsApplicationInfoAndEchoesRequestId() throws Exception {
        HttpResponse<String> response = get(serverPort, "/api/v1/meta", "it-request-0001");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\":\"flowpilot\"");
        assertThat(response.headers().firstValue(RequestIdFilter.HEADER)).hasValue("it-request-0001");
    }

    @Test
    void protectedApiPath_withoutToken_returns401Problem() throws Exception {
        HttpResponse<String> response = get(serverPort, "/api/v1/does-not-exist", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"requestId\"");
    }

    private HttpResponse<String> get(int port, String path, String requestId)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (requestId != null) {
            request.header(RequestIdFilter.HEADER, requestId);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
