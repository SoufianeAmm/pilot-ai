package com.flowpilot.organizations;

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
import org.springframework.jdbc.core.JdbcTemplate;

import com.flowpilot.support.PostgresIntegrationTest;
import com.flowpilot.support.RecordingInvitationMailer;
import com.flowpilot.support.TestHttp;
import com.flowpilot.support.TestUsers;
import com.flowpilot.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;

/** Organizations, members, roles and invitations over real HTTP against a real database. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(RecordingInvitationMailer.Config.class)
class OrganizationsIT extends PostgresIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    RecordingInvitationMailer mailer;

    @Autowired
    JdbcTemplate jdbc;

    private TestHttp http;

    @BeforeEach
    void setUp() {
        http = new TestHttp(port);
    }

    // ------------------------------------------------------------------ helpers

    private static String json(HttpResponse<String> response, String path) {
        Object value = JsonPath.read(response.body(), path);
        return String.valueOf(value);
    }

    private String createOrg(TestUser owner, String name) {
        HttpResponse<String> created = http.postJson("/api/v1/orgs", "{\"name\":\"%s\"}".formatted(name), owner.auth());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return json(created, "$.id");
    }

    private HttpResponse<String> invite(TestUser actor, String orgId, String email, String role) {
        return http.postJson("/api/v1/orgs/" + orgId + "/invitations",
                "{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role), actor.auth());
    }

    private HttpResponse<String> accept(TestUser user, String token) {
        return http.postJson("/api/v1/invitations/accept", "{\"token\":\"%s\"}".formatted(token), user.auth());
    }

    /** Invites and accepts in one go: returns once {@code user} is a member with {@code role}. */
    private void addMember(TestUser owner, String orgId, TestUser user, String role) {
        assertThat(invite(owner, orgId, user.email(), role).statusCode()).isEqualTo(201);
        assertThat(accept(user, mailer.tokenFor(user.email())).statusCode()).isEqualTo(200);
    }

    private long memberVersion(TestUser viewer, String orgId, TestUser member) {
        HttpResponse<String> list = http.get("/api/v1/orgs/" + orgId + "/members?size=100", viewer.auth());
        List<Object> versions = JsonPath.read(list.body(), "$.items[?(@.userId == '%s')].version".formatted(member.id()));
        return ((Number) versions.getFirst()).longValue();
    }

    private HttpResponse<String> changeRole(TestUser actor, String orgId, TestUser target, String role, long version) {
        return http.send("PATCH", "/api/v1/orgs/" + orgId + "/members/" + target.id(),
                "{\"role\":\"%s\",\"version\":%d}".formatted(role, version), actor.auth());
    }

    // ------------------------------------------------------------------ organizations

    @Test
    void create_makesTheCreatorOwner_andDerivesAUrlName() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");

        HttpResponse<String> created = http.postJson("/api/v1/orgs", "{\"name\":\"Équipe Données\"}", owner.auth());

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(json(created, "$.role")).isEqualTo("OWNER");
        assertThat(json(created, "$.slug")).startsWith("equipe-donnees");
        assertThat(json(created, "$.timeZone")).isEqualTo("UTC");
        HttpResponse<String> mine = http.get("/api/v1/orgs", owner.auth());
        assertThat(JsonPath.<List<String>>read(mine.body(), "$[*].id")).contains(json(created, "$.id"));
    }

    @Test
    void create_withATakenExplicitSlug_isRejectedWith409() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        String slug = "taken-" + owner.id().toString().substring(0, 8);
        http.postJson("/api/v1/orgs", "{\"name\":\"First\",\"slug\":\"%s\"}".formatted(slug), owner.auth());

        HttpResponse<String> second = http.postJson("/api/v1/orgs",
                "{\"name\":\"Second\",\"slug\":\"%s\"}".formatted(slug), owner.auth());

        assertThat(second.statusCode()).isEqualTo(409);
    }

    @Test
    void update_withAStaleVersion_isRejectedWith409() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        String orgId = createOrg(owner, "Versioned");
        assertThat(http.send("PATCH", "/api/v1/orgs/" + orgId, "{\"name\":\"Renamed\",\"version\":0}", owner.auth())
                .statusCode()).isEqualTo(200);

        HttpResponse<String> stale = http.send("PATCH", "/api/v1/orgs/" + orgId,
                "{\"name\":\"Again\",\"version\":0}", owner.auth());

        assertThat(stale.statusCode()).isEqualTo(409);
    }

    @Test
    void update_byAPlainMember_isForbidden_andTheDenialIsAudited() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser member = TestUsers.registerAndLogin(http, "Mia");
        String orgId = createOrg(owner, "Acme");
        addMember(owner, orgId, member, "MEMBER");

        HttpResponse<String> response = http.send("PATCH", "/api/v1/orgs/" + orgId,
                "{\"name\":\"Hacked\",\"version\":0}", member.auth());

        assertThat(response.statusCode()).isEqualTo(403);
        Integer denials = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'access.denied' "
                + "AND actor_id = ? AND details ->> 'permission' = 'ORG_UPDATE'", Integer.class, member.id());
        assertThat(denials).isEqualTo(1);
    }

    // ------------------------------------------------------------------ invitations

    @Test
    void invitation_acceptedByTheInvitedUser_makesThemAMemberWithTheInvitedRole() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser paul = TestUsers.registerAndLogin(http, "Paul");
        String orgId = createOrg(owner, "Acme");

        HttpResponse<String> invited = invite(owner, orgId, paul.email().toUpperCase(), "PROJECT_MANAGER");
        assertThat(invited.statusCode()).isEqualTo(201);
        assertThat(invited.body()).doesNotContain(mailer.tokenFor(paul.email()));

        HttpResponse<String> accepted = accept(paul, mailer.tokenFor(paul.email()));
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(json(accepted, "$.id")).isEqualTo(orgId);
        assertThat(json(accepted, "$.role")).isEqualTo("PROJECT_MANAGER");

        HttpResponse<String> members = http.get("/api/v1/orgs/" + orgId + "/members", paul.auth());
        assertThat(json(members, "$.totalElements")).isEqualTo("2");
    }

    @Test
    void invitation_isSingleUse() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser paul = TestUsers.registerAndLogin(http, "Paul");
        String orgId = createOrg(owner, "Acme");
        invite(owner, orgId, paul.email(), "MEMBER");
        String token = mailer.tokenFor(paul.email());
        accept(paul, token);

        assertThat(accept(paul, token).statusCode()).isEqualTo(404);
    }

    @Test
    void invitation_cannotBeAcceptedByAnotherAccount() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser paul = TestUsers.registerAndLogin(http, "Paul");
        TestUser mallory = TestUsers.registerAndLogin(http, "Mallory");
        String orgId = createOrg(owner, "Acme");
        invite(owner, orgId, paul.email(), "ADMIN");

        HttpResponse<String> stolen = accept(mallory, mailer.tokenFor(paul.email()));

        assertThat(stolen.statusCode()).isEqualTo(403);
        assertThat(http.get("/api/v1/orgs/" + orgId, mallory.auth()).statusCode()).isEqualTo(404);
    }

    @Test
    void invitation_revoked_canNoLongerBeAccepted() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser paul = TestUsers.registerAndLogin(http, "Paul");
        String orgId = createOrg(owner, "Acme");
        String invitationId = json(invite(owner, orgId, paul.email(), "MEMBER"), "$.id");

        assertThat(http.send("DELETE", "/api/v1/orgs/" + orgId + "/invitations/" + invitationId, null, owner.auth())
                .statusCode()).isEqualTo(204);

        assertThat(accept(paul, mailer.tokenFor(paul.email())).statusCode()).isEqualTo(404);
    }

    @Test
    void invitation_reInvitingReplacesThePreviousLink() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser paul = TestUsers.registerAndLogin(http, "Paul");
        String orgId = createOrg(owner, "Acme");
        invite(owner, orgId, paul.email(), "MEMBER");
        String firstToken = mailer.tokenFor(paul.email());

        invite(owner, orgId, paul.email(), "ADMIN");

        assertThat(accept(paul, firstToken).statusCode()).isEqualTo(404);
        HttpResponse<String> accepted = accept(paul, mailer.tokenFor(paul.email()));
        assertThat(json(accepted, "$.role")).isEqualTo("ADMIN");
        HttpResponse<String> pending = http.get("/api/v1/orgs/" + orgId + "/invitations", owner.auth());
        assertThat(JsonPath.<List<Object>>read(pending.body(), "$")).isEmpty();
    }

    @Test
    void invite_byAPlainMember_isForbidden() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser member = TestUsers.registerAndLogin(http, "Mia");
        String orgId = createOrg(owner, "Acme");
        addMember(owner, orgId, member, "MEMBER");

        assertThat(invite(member, orgId, "someone@example.com", "MEMBER").statusCode()).isEqualTo(403);
    }

    @Test
    void invite_anAdminCannotInviteAnOwner() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser admin = TestUsers.registerAndLogin(http, "Adam");
        String orgId = createOrg(owner, "Acme");
        addMember(owner, orgId, admin, "ADMIN");

        assertThat(invite(admin, orgId, "boss@example.com", "OWNER").statusCode()).isEqualTo(403);
        assertThat(invite(admin, orgId, "helper@example.com", "ADMIN").statusCode()).isEqualTo(201);
    }

    @Test
    void invite_anExistingMember_isRejectedWith409() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser member = TestUsers.registerAndLogin(http, "Mia");
        String orgId = createOrg(owner, "Acme");
        addMember(owner, orgId, member, "MEMBER");

        assertThat(invite(owner, orgId, member.email(), "ADMIN").statusCode()).isEqualTo(409);
    }

    // ------------------------------------------------------------------ roles and removal

    @Test
    void changeRole_byAnOwner_isApplied_andAudited() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser member = TestUsers.registerAndLogin(http, "Mia");
        String orgId = createOrg(owner, "Acme");
        addMember(owner, orgId, member, "MEMBER");

        HttpResponse<String> changed = changeRole(owner, orgId, member, "ADMIN", memberVersion(owner, orgId, member));

        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(json(changed, "$.role")).isEqualTo("ADMIN");
        Integer audits = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'org.member.role_changed' "
                + "AND outcome = 'SUCCESS' AND target_id = ? AND details ->> 'to' = 'ADMIN'", Integer.class, member.id());
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void changeRole_withAStaleVersion_isRejectedWith409() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser member = TestUsers.registerAndLogin(http, "Mia");
        String orgId = createOrg(owner, "Acme");
        addMember(owner, orgId, member, "MEMBER");
        long version = memberVersion(owner, orgId, member);
        changeRole(owner, orgId, member, "PROJECT_MANAGER", version);

        assertThat(changeRole(owner, orgId, member, "ADMIN", version).statusCode()).isEqualTo(409);
    }

    @Test
    void changeRole_anAdminCannotDemoteAnOwnerOrPromoteToOwner() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser admin = TestUsers.registerAndLogin(http, "Adam");
        TestUser member = TestUsers.registerAndLogin(http, "Mia");
        String orgId = createOrg(owner, "Acme");
        addMember(owner, orgId, admin, "ADMIN");
        addMember(owner, orgId, member, "MEMBER");

        assertThat(changeRole(admin, orgId, owner, "MEMBER", memberVersion(admin, orgId, owner)).statusCode())
                .isEqualTo(403);
        assertThat(changeRole(admin, orgId, member, "OWNER", memberVersion(admin, orgId, member)).statusCode())
                .isEqualTo(403);
    }

    @Test
    void changeRole_theLastOwnerCannotDemoteThemselves() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        String orgId = createOrg(owner, "Acme");

        HttpResponse<String> response = changeRole(owner, orgId, owner, "ADMIN", memberVersion(owner, orgId, owner));

        assertThat(response.statusCode()).isEqualTo(409);
    }

    @Test
    void leave_aMemberCanLeave_butTheLastOwnerCannot() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser member = TestUsers.registerAndLogin(http, "Mia");
        String orgId = createOrg(owner, "Acme");
        addMember(owner, orgId, member, "MEMBER");

        assertThat(http.send("DELETE", "/api/v1/orgs/" + orgId + "/members/" + member.id(), null, member.auth())
                .statusCode()).isEqualTo(204);
        assertThat(http.get("/api/v1/orgs/" + orgId, member.auth()).statusCode()).isEqualTo(404);

        assertThat(http.send("DELETE", "/api/v1/orgs/" + orgId + "/members/" + owner.id(), null, owner.auth())
                .statusCode()).isEqualTo(409);
    }

    @Test
    void remove_aPlainMemberCannotRemoveSomeoneElse() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        TestUser mia = TestUsers.registerAndLogin(http, "Mia");
        TestUser max = TestUsers.registerAndLogin(http, "Max");
        String orgId = createOrg(owner, "Acme");
        addMember(owner, orgId, mia, "MEMBER");
        addMember(owner, orgId, max, "MEMBER");

        assertThat(http.send("DELETE", "/api/v1/orgs/" + orgId + "/members/" + max.id(), null, mia.auth())
                .statusCode()).isEqualTo(403);
    }

    @Test
    void members_unknownSortField_isIgnoredInsteadOfFailing() {
        TestUser owner = TestUsers.registerAndLogin(http, "Olivia");
        String orgId = createOrg(owner, "Acme");

        HttpResponse<String> response = http.get("/api/v1/orgs/" + orgId + "/members?sort=passwordHash,asc", owner.auth());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
