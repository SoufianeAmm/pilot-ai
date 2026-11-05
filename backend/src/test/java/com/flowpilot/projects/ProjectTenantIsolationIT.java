package com.flowpilot.projects;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.flowpilot.support.PostgresIntegrationTest;
import com.flowpilot.support.TestHttp;
import com.flowpilot.support.TestUsers;
import com.flowpilot.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;

/**
 * Cross-tenant suite: Bob owns organization B and tries every project endpoint on Alice's
 * organization A. Every attempt must answer 404 (never 403, never data), exactly like a project id
 * that does not exist, so project ids cannot be probed. Mirrors the organizations
 * {@code TenantIsolationIT}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ProjectTenantIsolationIT extends PostgresIntegrationTest {

    @Value("${local.server.port}")
    int port;

    private TestHttp http;
    private TestUser alice;
    private TestUser bob;
    private String orgA;
    private String orgB;
    private String projectInA;

    @BeforeEach
    void setUp() {
        http = new TestHttp(port);
        alice = TestUsers.registerAndLogin(http, "Alice");
        bob = TestUsers.registerAndLogin(http, "Bob");
        orgA = JsonPath.read(http.postJson("/api/v1/orgs", "{\"name\":\"Org A\"}", alice.auth()).body(), "$.id");
        orgB = JsonPath.read(http.postJson("/api/v1/orgs", "{\"name\":\"Org B\"}", bob.auth()).body(), "$.id");
        // Alice creates a project in her organization A
        projectInA = JsonPath.read(http.postJson("/api/v1/orgs/" + orgA + "/projects",
                "{\"key\":\"FP\",\"name\":\"FlowPilot Demo\"}", alice.auth()).body(), "$.id");
    }

    private void assertHidden(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("FlowPilot Demo").doesNotContain(alice.email());
    }

    @Test
    void anotherTenantCannotListProjectsOfForeignOrg() {
        assertHidden(http.get("/api/v1/orgs/" + orgA + "/projects", bob.auth()));
    }

    @Test
    void anotherTenantCannotReadForeignProject() {
        assertHidden(http.get("/api/v1/orgs/" + orgA + "/projects/" + projectInA, bob.auth()));
    }

    @Test
    void anotherTenantCannotUpdateForeignProject() {
        assertHidden(http.send("PATCH", "/api/v1/orgs/" + orgA + "/projects/" + projectInA,
                "{\"name\":\"Pwned\",\"version\":0}", bob.auth()));
    }

    @Test
    void anotherTenantCannotListMembersOfForeignProject() {
        assertHidden(http.get("/api/v1/orgs/" + orgA + "/projects/" + projectInA + "/members", bob.auth()));
    }

    @Test
    void anotherTenantCannotAddMemberToForeignProject() {
        assertHidden(http.postJson("/api/v1/orgs/" + orgA + "/projects/" + projectInA + "/members",
                "{\"userId\":\"" + bob.id() + "\",\"role\":\"VIEWER\"}", bob.auth()));
    }

    @Test
    void anotherTenantCannotListMilestonesOfForeignProject() {
        assertHidden(http.get("/api/v1/orgs/" + orgA + "/projects/" + projectInA + "/milestones", bob.auth()));
    }

    @Test
    void anotherTenantCannotCreateMilestoneInForeignProject() {
        assertHidden(http.postJson("/api/v1/orgs/" + orgA + "/projects/" + projectInA + "/milestones",
                "{\"name\":\"M1\"}", bob.auth()));
    }

    @Test
    void aProjectIdFromAnotherOrgIsNotFoundThroughYourOwnOrg() {
        // Bob is an owner of B, but the project belongs to A: the tenant-scoped lookup must not find it
        assertHidden(http.get("/api/v1/orgs/" + orgB + "/projects/" + projectInA, bob.auth()));
    }

    @Test
    void foreignProjectLooksExactlyLikeOneThatDoesNotExist() {
        HttpResponse<String> foreign = http.get("/api/v1/orgs/" + orgA + "/projects/" + projectInA, bob.auth());
        HttpResponse<String> missing = http.get("/api/v1/orgs/" + orgA + "/projects/" + UUID.randomUUID(),
                bob.auth());

        assertThat(foreign.statusCode()).isEqualTo(missing.statusCode());
        assertThat(JsonPath.<String>read(foreign.body(), "$.detail"))
                .isEqualTo(JsonPath.<String>read(missing.body(), "$.detail"));
    }

    @Test
    void withoutATokenEveryProjectEndpointAnswers401() {
        assertThat(http.get("/api/v1/orgs/" + orgA + "/projects").statusCode()).isEqualTo(401);
        assertThat(http.get("/api/v1/orgs/" + orgA + "/projects/" + projectInA).statusCode()).isEqualTo(401);
        assertThat(http.get("/api/v1/orgs/" + orgA + "/projects/" + projectInA + "/members").statusCode())
                .isEqualTo(401);
    }

    @Test
    void aliceCanReadHerOwnProject() {
        // sanity check: the project actually exists and Alice (its org owner) can read it
        HttpResponse<String> own = http.get("/api/v1/orgs/" + orgA + "/projects/" + projectInA, alice.auth());
        assertThat(own.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(own.body(), "$.key")).isEqualTo("FP");
    }

    @Test
    void aliceCanListHerProjects() {
        List<String> keys = JsonPath.read(http.get("/api/v1/orgs/" + orgA + "/projects", alice.auth()).body(),
                "$.items[*].key");
        assertThat(keys).contains("FP");
    }
}
