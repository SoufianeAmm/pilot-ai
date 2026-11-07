package com.flowpilot.organizations;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;

import com.flowpilot.support.PostgresIntegrationTest;
import com.flowpilot.support.RecordingInvitationMailer;
import com.flowpilot.support.TestHttp;
import com.flowpilot.support.TestUsers;
import com.flowpilot.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;

/**
 * Cross-tenant suite: Bob owns organization B and tries every organization endpoint on Alice's
 * organization A. Every attempt must answer 404 (never 403, never data), exactly like an
 * organization id that does not exist, so ids cannot be probed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(RecordingInvitationMailer.Config.class)
class TenantIsolationIT extends PostgresIntegrationTest {

    @Value("${local.server.port}")
    int port;

    private TestHttp http;
    private TestUser alice;
    private TestUser bob;
    private String orgA;
    private String orgB;
    private String invitationInA;

    @BeforeEach
    void setUp() {
        http = new TestHttp(port);
        alice = TestUsers.registerAndLogin(http, "Alice");
        bob = TestUsers.registerAndLogin(http, "Bob");
        orgA = JsonPath.read(http.postJson("/api/v1/orgs", "{\"name\":\"Org A\"}", alice.auth()).body(), "$.id");
        orgB = JsonPath.read(http.postJson("/api/v1/orgs", "{\"name\":\"Org B\"}", bob.auth()).body(), "$.id");
        invitationInA = JsonPath.read(http.postJson("/api/v1/orgs/" + orgA + "/invitations",
                "{\"email\":\"guest-%s@example.com\",\"role\":\"MEMBER\"}".formatted(UUID.randomUUID()),
                alice.auth()).body(), "$.id");
    }

    private void assertHidden(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("Org A").doesNotContain(alice.email());
    }

    @Test
    void anotherTenantCannotReadTheOrganization() {
        assertHidden(http.get("/api/v1/orgs/" + orgA, bob.auth()));
    }

    @Test
    void anotherTenantCannotListMembers() {
        assertHidden(http.get("/api/v1/orgs/" + orgA + "/members", bob.auth()));
    }

    @Test
    void anotherTenantCannotUpdateTheOrganization() {
        assertHidden(http.send("PATCH", "/api/v1/orgs/" + orgA, "{\"name\":\"Pwned\",\"version\":0}", bob.auth()));
    }

    @Test
    void anotherTenantCannotChangeRolesOrRemoveMembers() {
        assertHidden(http.send("PATCH", "/api/v1/orgs/" + orgA + "/members/" + alice.id(),
                "{\"role\":\"MEMBER\",\"version\":0}", bob.auth()));
        assertHidden(http.send("DELETE", "/api/v1/orgs/" + orgA + "/members/" + alice.id(), null, bob.auth()));
    }

    @Test
    void anotherTenantCannotInviteListOrRevokeInvitations() {
        assertHidden(http.postJson("/api/v1/orgs/" + orgA + "/invitations",
                "{\"email\":\"spy@example.com\",\"role\":\"OWNER\"}", bob.auth()));
        assertHidden(http.get("/api/v1/orgs/" + orgA + "/invitations", bob.auth()));
        assertHidden(http.send("DELETE", "/api/v1/orgs/" + orgA + "/invitations/" + invitationInA, null, bob.auth()));
    }

    @Test
    void anInvitationIdFromAnotherOrganizationIsNotFoundThroughYourOwnOrganization() {
        // Bob is an owner of B, but the invitation belongs to A: tenant-scoped lookup must not find it
        assertHidden(http.send("DELETE", "/api/v1/orgs/" + orgB + "/invitations/" + invitationInA, null, bob.auth()));
    }

    @Test
    void anotherTenantsOrganizationLooksExactlyLikeOneThatDoesNotExist() {
        HttpResponse<String> foreign = http.get("/api/v1/orgs/" + orgA, bob.auth());
        HttpResponse<String> missing = http.get("/api/v1/orgs/" + UUID.randomUUID(), bob.auth());

        assertThat(foreign.statusCode()).isEqualTo(missing.statusCode());
        assertThat(JsonPath.<String>read(foreign.body(), "$.detail"))
                .isEqualTo(JsonPath.<String>read(missing.body(), "$.detail"));
    }

    @Test
    void listMine_onlyReturnsYourOwnOrganizations() {
        List<String> bobsOrgs = JsonPath.read(http.get("/api/v1/orgs", bob.auth()).body(), "$[*].id");

        assertThat(bobsOrgs).contains(orgB).doesNotContain(orgA);
    }

    @Test
    void withoutAToken_everyOrganizationEndpointAnswers401() {
        assertThat(http.get("/api/v1/orgs").statusCode()).isEqualTo(401);
        assertThat(http.get("/api/v1/orgs/" + orgA).statusCode()).isEqualTo(401);
        assertThat(http.get("/api/v1/orgs/" + orgA + "/members").statusCode()).isEqualTo(401);
    }
}
