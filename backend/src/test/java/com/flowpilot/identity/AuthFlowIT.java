package com.flowpilot.identity;

import static com.flowpilot.support.TestHttp.cookieValue;
import static com.flowpilot.support.TestHttp.setCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

import com.flowpilot.support.PostgresIntegrationTest;
import com.flowpilot.support.TestHttp;
import com.jayway.jsonpath.JsonPath;

/**
 * End-to-end authentication over real HTTP against a real database. The reuse grace period is
 * disabled so that reusing a rotated refresh token immediately counts as theft.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "flowpilot.security.refresh-token.reuse-grace=0s")
class AuthFlowIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String COOKIE = "fp_refresh";
    private static final String[] WEB_APP = {"X-Requested-With", "fetch", "Origin", "http://localhost:5173"};

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private TestHttp http;

    @BeforeEach
    void setUp() {
        http = new TestHttp(port);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private HttpResponse<String> register(String email, String password) {
        return http.postJson("/api/v1/auth/register",
                "{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"Test User\"}".formatted(email, password));
    }

    private HttpResponse<String> login(String email, String password) {
        return http.postJson("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private HttpResponse<String> refresh(String refreshToken, String... headers) {
        String[] all = new String[headers.length + 2];
        all[0] = "Cookie";
        all[1] = COOKIE + "=" + refreshToken;
        System.arraycopy(headers, 0, all, 2, headers.length);
        return http.post("/api/v1/auth/refresh", all);
    }

    private static String json(HttpResponse<String> response, String path) {
        return JsonPath.read(response.body(), path).toString();
    }

    @Test
    void register_login_andCallMe_withTheAccessToken() {
        String email = uniqueEmail();

        HttpResponse<String> registered = register(email, PASSWORD);
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(json(registered, "$.email")).isEqualTo(email);

        HttpResponse<String> loggedIn = login(email.toUpperCase(), PASSWORD);
        assertThat(loggedIn.statusCode()).isEqualTo(200);
        assertThat(json(loggedIn, "$.tokenType")).isEqualTo("Bearer");
        assertThat(Integer.parseInt(json(loggedIn, "$.expiresIn"))).isBetween(890, 900);
        assertThat(setCookie(loggedIn, COOKIE)).hasValueSatisfying(cookie -> assertThat(cookie)
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth"));

        HttpResponse<String> me = http.get("/api/v1/me", "Authorization", "Bearer " + json(loggedIn, "$.accessToken"));
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json(me, "$.email")).isEqualTo(email);
        assertThat(json(me, "$.displayName")).isEqualTo("Test User");
    }

    @Test
    void register_passwordIsStoredAsArgon2Hash_neverInPlainText() {
        String email = uniqueEmail();
        register(email, PASSWORD);

        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE email = ?", String.class, email);

        assertThat(hash).startsWith("{argon2@SpringSecurity_v5_8}").doesNotContain(PASSWORD);
    }

    @Test
    void register_sameEmailWithDifferentCase_isRejectedWith409() {
        String email = uniqueEmail();
        register(email, PASSWORD);

        HttpResponse<String> duplicate = register(email.toUpperCase(), PASSWORD);

        assertThat(duplicate.statusCode()).isEqualTo(409);
    }

    @Test
    void register_shortPassword_isRejectedWithFieldError() {
        HttpResponse<String> response = register(uniqueEmail(), "short");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response, "$.errors[0].field")).isEqualTo("password");
    }

    @Test
    void login_wrongPasswordAndUnknownEmail_returnTheSameAnswer() {
        String email = uniqueEmail();
        register(email, PASSWORD);

        HttpResponse<String> wrongPassword = login(email, "wrong password 123");
        HttpResponse<String> unknownEmail = login(uniqueEmail(), PASSWORD);

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(401);
        assertThat(json(wrongPassword, "$.detail")).isEqualTo(json(unknownEmail, "$.detail"));
    }

    @Test
    void login_failure_isWrittenToTheAuditLog() {
        String email = uniqueEmail();
        login(email, "wrong password 123");

        Integer entries = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'auth.login.failed' AND outcome = 'FAILURE' "
                        + "AND details ->> 'email' = ?", Integer.class, email);

        assertThat(entries).isEqualTo(1);
    }

    @Test
    void auditLog_cannotBeModifiedByTheApplicationRole() {
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'tampered'"))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log"))
                .rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void login_isBlockedAfterFiveFailures_evenWithTheCorrectPassword() {
        String email = uniqueEmail();
        register(email, PASSWORD);
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "wrong password " + i).statusCode()).isEqualTo(401);
        }

        HttpResponse<String> blocked = login(email, PASSWORD);

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).hasValueSatisfying(
                seconds -> assertThat(Long.parseLong(seconds)).isBetween(1L, 900L));
    }

    @Test
    void me_withoutOrWithInvalidToken_returns401() {
        assertThat(http.get("/api/v1/me").statusCode()).isEqualTo(401);
        assertThat(http.get("/api/v1/me", "Authorization", "Bearer not.a.token").statusCode()).isEqualTo(401);
    }

    @Test
    void refresh_withoutTheWebAppHeader_orFromAForeignOrigin_isForbidden() {
        String email = uniqueEmail();
        register(email, PASSWORD);
        String refreshToken = cookieValue(login(email, PASSWORD), COOKIE);

        assertThat(refresh(refreshToken).statusCode()).isEqualTo(403);
        assertThat(refresh(refreshToken, "X-Requested-With", "fetch", "Origin", "https://evil.example")
                .statusCode()).isEqualTo(403);
    }

    @Test
    void refresh_rotatesTheToken_andReusingAnOldTokenEndsTheWholeSession() {
        String email = uniqueEmail();
        register(email, PASSWORD);
        String first = cookieValue(login(email, PASSWORD), COOKIE);

        HttpResponse<String> rotated = refresh(first, WEB_APP);
        assertThat(rotated.statusCode()).isEqualTo(200);
        assertThat(json(rotated, "$.accessToken")).isNotBlank();
        String second = cookieValue(rotated, COOKIE);
        assertThat(second).isNotEqualTo(first);

        // an attacker replays the first (already rotated) token
        HttpResponse<String> replay = refresh(first, WEB_APP);
        assertThat(replay.statusCode()).isEqualTo(401);
        assertThat(setCookie(replay, COOKIE)).hasValueSatisfying(cookie -> assertThat(cookie).contains("Max-Age=0"));

        // the legitimate newest token is now revoked too
        assertThat(refresh(second, WEB_APP).statusCode()).isEqualTo(401);
        Integer reuseAudits = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log a JOIN app_user u ON u.id = a.actor_id "
                        + "WHERE u.email = ? AND a.action = 'auth.refresh.reuse_detected'", Integer.class, email);
        assertThat(reuseAudits).isEqualTo(1);
    }

    @Test
    void logout_revokesTheRefreshToken_andClearsTheCookie() {
        String email = uniqueEmail();
        register(email, PASSWORD);
        String refreshToken = cookieValue(login(email, PASSWORD), COOKIE);

        HttpResponse<String> logout = http.post("/api/v1/auth/logout",
                "Cookie", COOKIE + "=" + refreshToken, "X-Requested-With", "fetch");

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(setCookie(logout, COOKIE)).hasValueSatisfying(cookie -> assertThat(cookie).contains("Max-Age=0"));
        assertThat(refresh(refreshToken, WEB_APP).statusCode()).isEqualTo(401);
    }
}
