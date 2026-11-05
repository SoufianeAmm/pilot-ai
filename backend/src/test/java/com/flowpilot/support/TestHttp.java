package com.flowpilot.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/** Minimal real-HTTP client for integration tests (no cookie jar: cookies are passed explicitly). */
public final class TestHttp {

    private final HttpClient client = HttpClient.newHttpClient();
    private final String baseUrl;

    public TestHttp(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    /** @param headers alternating names and values */
    public HttpResponse<String> get(String path, String... headers) {
        return send(request(path, headers).GET());
    }

    public HttpResponse<String> postJson(String path, String json, String... headers) {
        return send(request(path, headers).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public HttpResponse<String> post(String path, String... headers) {
        return send(request(path, headers).POST(HttpRequest.BodyPublishers.noBody()));
    }

    /** Any method, e.g. PATCH or DELETE; {@code json} may be null for no body. */
    public HttpResponse<String> send(String method, String path, String json, String... headers) {
        HttpRequest.Builder builder = request(path, headers);
        if (json == null) {
            return send(builder.method(method, HttpRequest.BodyPublishers.noBody()));
        }
        return send(builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json)));
    }

    /** The full Set-Cookie header for the named cookie, e.g. "fp_refresh=...; Path=/api/v1/auth; HttpOnly". */
    public static Optional<String> setCookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst();
    }

    public static String cookieValue(HttpResponse<?> response, String name) {
        String header = setCookie(response, name).orElseThrow(() -> new AssertionError("no Set-Cookie for " + name));
        String pair = header.split(";", 2)[0];
        return pair.substring(name.length() + 1);
    }

    private HttpRequest.Builder request(String path, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
