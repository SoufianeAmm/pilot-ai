package com.flowpilot.support;

import java.net.http.HttpResponse;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

/** Creates real accounts through the API and returns their access tokens. */
public final class TestUsers {

    public static final String PASSWORD = "correct horse battery staple";

    private TestUsers() {
    }

    public record TestUser(UUID id, String email, String token) {

        /** Header pair for TestHttp: {@code http.get(path, user.auth())}. */
        public String[] auth() {
            return new String[] {"Authorization", "Bearer " + token};
        }
    }

    public static TestUser registerAndLogin(TestHttp http, String name) {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registered = http.postJson("/api/v1/auth/register",
                "{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"%s\"}".formatted(email, PASSWORD, name));
        if (registered.statusCode() != 201) {
            throw new AssertionError("registration failed: " + registered.body());
        }
        HttpResponse<String> login = http.postJson("/api/v1/auth/login",
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
        return new TestUser(UUID.fromString(JsonPath.read(registered.body(), "$.id")), email,
                JsonPath.read(login.body(), "$.accessToken"));
    }
}
