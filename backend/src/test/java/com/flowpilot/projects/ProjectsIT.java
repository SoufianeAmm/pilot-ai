package com.flowpilot.projects;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Projects, project members and milestones inside one organization, over real HTTP against a real
 * database. Cross-organization isolation is covered by {@link ProjectTenantIsolationIT}; this suite
 * covers isolation between projects of the same organization and the member/milestone rules.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(RecordingInvitationMailer.Config.class)
class ProjectsIT extends PostgresIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    RecordingInvitationMailer mailer;

    private TestHttp http;
    private TestUser owner;
    private String orgId;

    @BeforeEach
    void setUp() {
        http = new TestHttp(port);
        owner = TestUsers.registerAndLogin(http, "Owner");
        orgId = json(http.postJson("/api/v1/orgs", "{\"name\":\"Acme\"}", owner.auth()), "$.id");
    }

    // ------------------------------------------------------------------ helpers

    private static String json(HttpResponse<String> response, String path) {
        return String.valueOf((Object) JsonPath.read(response.body(), path));
    }

    private String projects() {
        return "/api/v1/orgs/" + orgId + "/projects";
    }

    private TestUser orgMember(String name, String orgRole) {
        TestUser user = TestUsers.registerAndLogin(http, name);
        http.postJson("/api/v1/orgs/" + orgId + "/invitations",
                "{\"email\":\"%s\",\"role\":\"%s\"}".formatted(user.email(), orgRole), owner.auth());
        HttpResponse<String> accepted = http.postJson("/api/v1/invitations/accept",
                "{\"token\":\"%s\"}".formatted(mailer.tokenFor(user.email())), user.auth());
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        return user;
    }

    private String createProject(TestUser actor, String key, String name) {
        HttpResponse<String> created = http.postJson(projects(),
                "{\"key\":\"%s\",\"name\":\"%s\"}".formatted(key, name), actor.auth());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return json(created, "$.id");
    }

    private HttpResponse<String> addMember(String projectId, TestUser user, String role) {
        return http.postJson(projects() + "/" + projectId + "/members",
                "{\"userId\":\"%s\",\"role\":\"%s\",\"weeklyCapacityPoints\":10}".formatted(user.id(), role),
                owner.auth());
    }

    private String createMilestone(String projectId, String name) {
        HttpResponse<String> created = http.postJson(projects() + "/" + projectId + "/milestones",
                "{\"name\":\"%s\"}".formatted(name), owner.auth());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        return json(created, "$.id");
    }

    // ------------------------------------------------------------------ members

    @Test
    void addMember_makesAnOrgMemberAProjectMember_once() {
        TestUser carol = orgMember("Carol", "MEMBER");
        String project = createProject(owner, "FP", "FlowPilot");

        HttpResponse<String> added = addMember(project, carol, "CONTRIBUTOR");
        assertThat(added.statusCode()).as(added.body()).isEqualTo(200);
        assertThat(json(added, "$.role")).isEqualTo("CONTRIBUTOR");
        assertThat(json(added, "$.weeklyCapacityPoints")).isEqualTo("10");

        HttpResponse<String> again = addMember(project, carol, "VIEWER");
        assertThat(again.statusCode()).as(again.body()).isEqualTo(409);
        assertThat(http.get(projects() + "/" + project, carol.auth()).statusCode()).isEqualTo(200);
    }

    @Test
    void addMember_rejectsAnOutOfRangeCapacity() {
        TestUser carol = orgMember("Carol", "MEMBER");
        String project = createProject(owner, "FP", "FlowPilot");

        HttpResponse<String> response = http.postJson(projects() + "/" + project + "/members",
                "{\"userId\":\"%s\",\"role\":\"VIEWER\",\"weeklyCapacityPoints\":101}".formatted(carol.id()),
                owner.auth());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ visibility

    @Test
    void projectList_showsOrgMembersOnlyTheirOwnProjects() {
        TestUser carol = orgMember("Carol", "MEMBER");
        String mine = createProject(owner, "MINE", "Carol's project");
        createProject(owner, "SECRET", "Secret project");
        addMember(mine, carol, "VIEWER");

        List<String> carolSees = JsonPath.read(http.get(projects(), carol.auth()).body(), "$.items[*].key");
        List<String> ownerSees = JsonPath.read(http.get(projects(), owner.auth()).body(), "$.items[*].key");

        assertThat(carolSees).containsExactly("MINE");
        assertThat(ownerSees).containsExactlyInAnyOrder("MINE", "SECRET");
    }

    @Test
    void projectManager_canOpenAndManageTheProjectTheyCreated() {
        TestUser paul = orgMember("Paul", "PROJECT_MANAGER");

        String project = createProject(paul, "PAUL", "Paul's project");

        assertThat(http.get(projects() + "/" + project, paul.auth()).statusCode()).isEqualTo(200);
        HttpResponse<String> milestone = http.postJson(projects() + "/" + project + "/milestones",
                "{\"name\":\"M1\"}", paul.auth());
        assertThat(milestone.statusCode()).as(milestone.body()).isEqualTo(200);
        List<String> roles = JsonPath.read(http.get(projects() + "/" + project + "/members", paul.auth()).body(),
                "$[?(@.userId == '%s')].role".formatted(paul.id()));
        assertThat(roles).containsExactly("MANAGER");
    }

    // ------------------------------------------------------------------ milestones

    @Test
    void milestoneOfAnotherProject_isNotFoundThroughYourProject() {
        TestUser carol = orgMember("Carol", "MEMBER");
        String hers = createProject(owner, "HERS", "Carol manages this");
        String secret = createProject(owner, "SECRET", "Carol is not in this");
        addMember(hers, carol, "MANAGER");
        String secretMilestone = createMilestone(secret, "Secret milestone");
        String viaHers = projects() + "/" + hers + "/milestones/" + secretMilestone;

        assertThat(http.get(viaHers, carol.auth()).statusCode()).isEqualTo(404);
        assertThat(http.postJson(viaHers + "/complete", "{\"version\":0}", carol.auth()).statusCode()).isEqualTo(404);
        assertThat(http.send("PATCH", viaHers, "{\"name\":\"Pwned\",\"version\":0}", carol.auth()).statusCode())
                .isEqualTo(404);

        HttpResponse<String> untouched = http.get(projects() + "/" + secret + "/milestones/" + secretMilestone,
                owner.auth());
        assertThat(json(untouched, "$.name")).isEqualTo("Secret milestone");
        assertThat(json(untouched, "$.status")).isEqualTo("OPEN");
    }

    @Test
    void updateMilestone_changesTheNameAndRejectsAStaleVersion() {
        String project = createProject(owner, "FP", "FlowPilot");
        String milestone = createMilestone(project, "Beta");
        String path = projects() + "/" + project + "/milestones/" + milestone;

        HttpResponse<String> renamed = http.send("PATCH", path,
                "{\"name\":\"Beta release\",\"dueDate\":\"2026-12-15\",\"version\":0}", owner.auth());
        assertThat(renamed.statusCode()).as(renamed.body()).isEqualTo(200);
        assertThat(json(renamed, "$.name")).isEqualTo("Beta release");
        assertThat(json(renamed, "$.dueDate")).isEqualTo("2026-12-15");

        HttpResponse<String> stale = http.send("PATCH", path, "{\"name\":\"Lost update\",\"version\":0}", owner.auth());
        assertThat(stale.statusCode()).isEqualTo(409);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void targetDateBeforeStartDate_isAValidationError() {
        HttpResponse<String> created = http.postJson(projects(),
                "{\"key\":\"DATES\",\"name\":\"D\",\"startDate\":\"2026-12-01\",\"targetDate\":\"2026-01-01\"}",
                owner.auth());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(400);
        assertThat(created.body()).contains("target date");

        String project = createProject(owner, "FP", "FlowPilot");
        HttpResponse<String> updated = http.send("PATCH", projects() + "/" + project,
                "{\"startDate\":\"2026-12-01\",\"targetDate\":\"2026-01-01\",\"version\":0}", owner.auth());
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(400);
    }

    @Test
    void duplicateKey_isAConflict_whateverTheCase() {
        createProject(owner, "FP", "First");

        HttpResponse<String> duplicate = http.postJson(projects(), "{\"key\":\"fp\",\"name\":\"Second\"}", owner.auth());

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("key already exists");
    }
}
