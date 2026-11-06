package com.flowpilot.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
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
 * Integration tests for the tasks module, covering the security boundaries called out in review:
 * cross-project access within the same org → 404; foreign assignee/milestone/dependency rejected;
 * deleting a task unblocks its dependents; cycle and self-dependency → 422 (not 409).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TasksIT extends PostgresIntegrationTest {

    @Value("${local.server.port}")
    int port;

    private TestHttp http;
    private TestUser alice;
    private TestUser bob;
    private String orgA;
    private String one;
    private String secret;
    private String taskInSecret;
    private String milestoneInSecret;

    @BeforeEach
    void setUp() {
        http = new TestHttp(port);
        alice = TestUsers.registerAndLogin(http, "Alice");
        bob = TestUsers.registerAndLogin(http, "Bob");
        orgA = JsonPath.read(http.postJson("/api/v1/orgs", "{\"name\":\"Org A\"}", alice.auth()).body(), "$.id");
        one = JsonPath.read(http.postJson("/api/v1/orgs/" + orgA + "/projects",
                "{\"key\":\"ONE\",\"name\":\"Project One\"}", alice.auth()).body(), "$.id");
        secret = JsonPath.read(http.postJson("/api/v1/orgs/" + orgA + "/projects",
                "{\"key\":\"SEC\",\"name\":\"Project Secret\"}", alice.auth()).body(), "$.id");
        taskInSecret = JsonPath.read(http.postJson("/api/v1/orgs/" + orgA + "/projects/" + secret + "/tasks",
                "{\"title\":\"Secret task\"}", alice.auth()).body(), "$.id");
        milestoneInSecret = JsonPath.read(http.postJson("/api/v1/orgs/" + orgA + "/projects/" + secret + "/milestones",
                "{\"name\":\"Secret milestone\"}", alice.auth()).body(), "$.id");
    }

    @Test
    void crossProjectTaskRead_isNotFound() {
        assertNotFound(http.get("/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks/" + taskInSecret, alice.auth()));
    }

    @Test
    void crossProjectTaskUpdate_isNotFound() {
        assertNotFound(http.send("PATCH", "/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks/" + taskInSecret,
                "{\"title\":\"Pwned\",\"version\":0}", alice.auth()));
    }

    @Test
    void crossProjectTaskDelete_isNotFound() {
        assertNotFound(http.send("DELETE", "/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks/" + taskInSecret,
                null, alice.auth()));
    }

    @Test
    void crossProjectDependency_isNotFound() {
        String taskOne = createTask(one, "Task in ONE");
        assertNotFound(http.send("PUT", "/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks/" + taskOne
                + "/dependencies/" + taskInSecret, null, alice.auth()));
    }

    @Test
    void foreignAssignee_isRejected() {
        HttpResponse<String> resp = http.postJson("/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks",
                "{\"title\":\"Assigned to outsider\",\"assigneeId\":\"" + bob.id() + "\"}", alice.auth());
        assertThat(resp.statusCode()).as(resp.body()).isEqualTo(400);
    }

    @Test
    void foreignMilestone_isRejected() {
        HttpResponse<String> resp = http.postJson("/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks",
                "{\"title\":\"Wrong milestone\",\"milestoneId\":\"" + milestoneInSecret + "\"}", alice.auth());
        assertThat(resp.statusCode()).as(resp.body()).isEqualTo(400);
    }

    @Test
    void deletingATaskUnblocksItsDependents() {
        String t1 = createTask(one, "Blocker");
        String t2 = createTask(one, "Blocked");
        HttpResponse<String> add = http.send("PUT", "/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks/" + t2
                + "/dependencies/" + t1, null, alice.auth());
        assertThat(add.statusCode()).isEqualTo(204);
        assertThat(isBlocked(t2)).isTrue();
        HttpResponse<String> del = http.send("DELETE", "/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks/" + t1,
                null, alice.auth());
        assertThat(del.statusCode()).isEqualTo(204);
        assertThat(isBlocked(t2)).isFalse();
    }

    @Test
    void cycle_isRejectedWith422() {
        String t1 = createTask(one, "A");
        String t2 = createTask(one, "B");
        http.send("PUT", "/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks/" + t2 + "/dependencies/" + t1,
                null, alice.auth());
        HttpResponse<String> cycle = http.send("PUT", "/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks/" + t1
                + "/dependencies/" + t2, null, alice.auth());
        assertThat(cycle.statusCode()).as(cycle.body()).isEqualTo(422);
        assertThat(cycle.body()).contains("cycle");
    }

    @Test
    void selfDependency_isRejectedWith422() {
        String t1 = createTask(one, "Self");
        HttpResponse<String> self = http.send("PUT", "/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks/" + t1
                + "/dependencies/" + t1, null, alice.auth());
        assertThat(self.statusCode()).as(self.body()).isEqualTo(422);
    }

    private String createTask(String projectId, String title) {
        return JsonPath.read(http.postJson("/api/v1/orgs/" + orgA + "/projects/" + projectId + "/tasks",
                "{\"title\":\"" + title + "\"}", alice.auth()).body(), "$.id");
    }

    private boolean isBlocked(String taskId) {
        String body = http.get("/api/v1/orgs/" + orgA + "/projects/" + one + "/tasks/" + taskId, alice.auth()).body();
        return JsonPath.read(body, "$.blocked");
    }

    private static void assertNotFound(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("Secret");
    }
}
