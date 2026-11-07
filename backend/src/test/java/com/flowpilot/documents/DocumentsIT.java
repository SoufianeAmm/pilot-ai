package com.flowpilot.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

import com.flowpilot.support.PostgresIntegrationTest;
import com.flowpilot.support.RecordingInvitationMailer;
import com.flowpilot.support.TestHttp;
import com.flowpilot.support.TestUsers;
import com.flowpilot.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;

/**
 * Access control and tenant isolation of the documents module, over real HTTP against a real
 * database. An outsider (member of no organization, or of another one) must get 404 everywhere, so
 * they cannot even tell that a project exists; project roles decide who may upload and delete.
 * Files are stored in a temporary folder that is removed afterwards.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(RecordingInvitationMailer.Config.class)
class DocumentsIT extends PostgresIntegrationTest {

    private static final String SECRET_NAME = "board-secret.pdf";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Path STORAGE = createStorageDirectory();

    @DynamicPropertySource
    static void documentStorage(DynamicPropertyRegistry registry) {
        registry.add("flowpilot.documents.storage-path", STORAGE::toString);
    }

    @AfterAll
    static void deleteStorage() throws IOException {
        FileSystemUtils.deleteRecursively(STORAGE);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    RecordingInvitationMailer mailer;

    @Autowired
    JdbcClient jdbc;

    private TestHttp http;
    private TestUser owner;
    private TestUser contributor;
    private TestUser viewer;
    private TestUser outsider;
    private TestUser otherOwner;
    private String orgId;
    private String projectId;
    private String otherProjectId;
    private String foreignOrgId;
    private String foreignProjectId;

    @BeforeEach
    void setUp() {
        http = new TestHttp(port);
        owner = TestUsers.registerAndLogin(http, "Owner");
        outsider = TestUsers.registerAndLogin(http, "Outsider");
        otherOwner = TestUsers.registerAndLogin(http, "Mallory");

        orgId = json(http.postJson("/api/v1/orgs", "{\"name\":\"Acme\"}", owner.auth()), "$.id");
        projectId = createProject(owner, orgId, "ONE", "Project One");
        otherProjectId = createProject(owner, orgId, "TWO", "Project Two");

        contributor = orgMember("Carol");
        addMember(projectId, contributor, "CONTRIBUTOR");
        viewer = orgMember("Victor");
        addMember(projectId, viewer, "VIEWER");

        foreignOrgId = json(http.postJson("/api/v1/orgs", "{\"name\":\"Foreign\"}", otherOwner.auth()), "$.id");
        foreignProjectId = createProject(otherOwner, foreignOrgId, "MAL", "Foreign Project");
    }

    // ------------------------------------------------------------------ outsiders

    @Test
    void outsiderInNoOrganization_getsNotFoundOnEveryEndpoint() {
        String doc = uploadOk(owner, projectId, SECRET_NAME);

        assertNotFound(http.get(docs(projectId), outsider.auth()));
        assertNotFound(http.get(docs(projectId) + "/" + doc, outsider.auth()));
        assertNotFound(http.get(docs(projectId) + "/" + doc + "/content", outsider.auth()));
        assertNotFound(upload(outsider, projectId, "intruder.pdf"));
        assertNotFound(http.send("DELETE", docs(projectId) + "/" + doc, null, outsider.auth()));
        assertNotFound(http.postJson(knowledge(projectId), "{\"question\":\"anything\"}", outsider.auth()));

        // nothing was added or removed by the attempts
        HttpResponse<String> list = http.get(docs(projectId), owner.auth());
        assertThat(JsonPath.<Integer>read(list.body(), "$.totalElements")).isEqualTo(1);
    }

    @Test
    void memberOfAnotherOrganization_getsNotFound() {
        String doc = uploadOk(owner, projectId, SECRET_NAME);

        assertNotFound(http.get(docs(projectId), otherOwner.auth()));
        assertNotFound(http.get(docs(projectId) + "/" + doc, otherOwner.auth()));
        assertNotFound(upload(otherOwner, projectId, "intruder.pdf"));
        assertNotFound(http.send("DELETE", docs(projectId) + "/" + doc, null, otherOwner.auth()));
    }

    @Test
    void unknownProject_isNotFound_evenForAnOrganizationOwner() {
        String missing = UUID.randomUUID().toString();

        assertNotFound(http.get(docs(missing), owner.auth()));
        assertNotFound(upload(owner, missing, "ghost.pdf"));
        assertNotFound(http.postJson(knowledge(missing), "{\"question\":\"anything\"}", owner.auth()));
    }

    @Test
    void memberOfOneProject_cannotReachAnotherProjectOfTheSameOrganization() {
        uploadOk(owner, otherProjectId, SECRET_NAME);

        assertNotFound(http.get(docs(otherProjectId), contributor.auth()));
        assertNotFound(upload(contributor, otherProjectId, "intruder.pdf"));
    }

    @Test
    void documentOfAnotherProject_isNotReachableThroughThisProject() {
        String doc = uploadOk(owner, otherProjectId, SECRET_NAME);

        assertNotFound(http.get(docs(projectId) + "/" + doc, owner.auth()));
        assertNotFound(http.get(docs(projectId) + "/" + doc + "/content", owner.auth()));
        assertNotFound(http.send("DELETE", docs(projectId) + "/" + doc, null, owner.auth()));
        // and it is still there in its own project
        assertThat(http.get(docs(otherProjectId) + "/" + doc, owner.auth()).statusCode()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ project roles

    @Test
    void viewer_canRead_butCannotUpload() {
        String doc = uploadOk(owner, projectId, "report.pdf");

        assertThat(http.get(docs(projectId), viewer.auth()).statusCode()).isEqualTo(200);
        assertThat(http.get(docs(projectId) + "/" + doc, viewer.auth()).statusCode()).isEqualTo(200);
        assertThat(http.get(docs(projectId) + "/" + doc + "/content", viewer.auth()).statusCode()).isEqualTo(200);

        HttpResponse<String> denied = upload(viewer, projectId, "viewer-upload.pdf");
        assertThat(denied.statusCode()).as(denied.body()).isEqualTo(403);
    }

    @Test
    void viewer_cannotDeleteSomeoneElsesDocument() {
        String doc = uploadOk(owner, projectId, "report.pdf");

        HttpResponse<String> denied = http.send("DELETE", docs(projectId) + "/" + doc, null, viewer.auth());
        assertThat(denied.statusCode()).as(denied.body()).isEqualTo(403);
    }

    @Test
    void contributor_cannotDeleteSomeoneElsesDocument() {
        String ownersDoc = uploadOk(owner, projectId, "owners.pdf");

        HttpResponse<String> denied = http.send("DELETE", docs(projectId) + "/" + ownersDoc, null, contributor.auth());
        assertThat(denied.statusCode()).as(denied.body()).isEqualTo(403);
        assertThat(http.get(docs(projectId) + "/" + ownersDoc, owner.auth()).statusCode()).isEqualTo(200);
    }

    @Test
    void contributor_canDeleteTheirOwnDocument() {
        String mine = uploadOk(contributor, projectId, "mine.pdf");

        HttpResponse<String> deleted = http.send("DELETE", docs(projectId) + "/" + mine, null, contributor.auth());
        assertThat(deleted.statusCode()).as(deleted.body()).isEqualTo(204);
        assertThat(http.get(docs(projectId) + "/" + mine, owner.auth()).statusCode()).isEqualTo(404);
    }

    @Test
    void manager_canDeleteAnyDocument() {
        String contributorsDoc = uploadOk(contributor, projectId, "carols.pdf");

        // an organization owner has an implicit MANAGER role on every project
        HttpResponse<String> deleted = http.send("DELETE", docs(projectId) + "/" + contributorsDoc, null,
                owner.auth());
        assertThat(deleted.statusCode()).as(deleted.body()).isEqualTo(204);
        assertThat(http.get(docs(projectId) + "/" + contributorsDoc, owner.auth()).statusCode()).isEqualTo(404);
    }

    @Test
    void sameFileUploadedTwiceToOneProject_isRejected() {
        byte[] pdf = pdfBytes();
        assertThat(upload(owner, projectId, "first.pdf", pdf).statusCode()).isEqualTo(201);

        HttpResponse<String> again = upload(owner, projectId, "second.pdf", pdf);
        assertThat(again.statusCode()).as(again.body()).isEqualTo(409);
    }

    // ------------------------------------------------------------------ the database itself

    @Test
    void database_refusesDocumentsInUnknownOrForeignProjects() {
        // control: a document in a real project of its own organization is accepted
        assertThat(insertDocument(orgId, projectId)).isEqualTo(1);

        // a project that does not exist
        assertThatThrownBy(() -> insertDocument(orgId, UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // a real project, but of another organization
        assertThatThrownBy(() -> insertDocument(orgId, foreignProjectId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // the same project id under the wrong organization
        assertThatThrownBy(() -> insertDocument(foreignOrgId, projectId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------ helpers

    private int insertDocument(String org, String project) {
        return jdbc.sql("""
                INSERT INTO document (id, org_id, project_id, filename, content_type, size_bytes, sha256,
                                      storage_key, status, uploaded_by, created_at, updated_at, version)
                VALUES (:id, :org, :project, 'direct.pdf', 'application/pdf', 1, :sha, :key, 'PENDING',
                        :user, now(), now(), 0)
                """)
                .param("id", UUID.randomUUID())
                .param("org", UUID.fromString(org))
                .param("project", UUID.fromString(project))
                .param("sha", new byte[32])
                .param("key", "it/" + UUID.randomUUID())
                .param("user", owner.id())
                .update();
    }

    private static String json(HttpResponse<String> response, String path) {
        return String.valueOf((Object) JsonPath.read(response.body(), path));
    }

    private String docs(String project) {
        return "/api/v1/orgs/" + orgId + "/projects/" + project + "/documents";
    }

    private String knowledge(String project) {
        return "/api/v1/orgs/" + orgId + "/projects/" + project + "/knowledge/query";
    }

    private String createProject(TestUser actor, String org, String key, String name) {
        HttpResponse<String> created = http.postJson("/api/v1/orgs/" + org + "/projects",
                "{\"key\":\"%s\",\"name\":\"%s\"}".formatted(key, name), actor.auth());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return json(created, "$.id");
    }

    /** Registers a user and makes them a plain member of the organization (no project yet). */
    private TestUser orgMember(String name) {
        TestUser user = TestUsers.registerAndLogin(http, name);
        http.postJson("/api/v1/orgs/" + orgId + "/invitations",
                "{\"email\":\"%s\",\"role\":\"MEMBER\"}".formatted(user.email()), owner.auth());
        HttpResponse<String> accepted = http.postJson("/api/v1/invitations/accept",
                "{\"token\":\"%s\"}".formatted(mailer.tokenFor(user.email())), user.auth());
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        return user;
    }

    private void addMember(String project, TestUser user, String role) {
        HttpResponse<String> added = http.postJson(
                "/api/v1/orgs/" + orgId + "/projects/" + project + "/members",
                "{\"userId\":\"%s\",\"role\":\"%s\",\"weeklyCapacityPoints\":10}".formatted(user.id(), role),
                owner.auth());
        assertThat(added.statusCode()).as(added.body()).isEqualTo(200);
    }

    private String uploadOk(TestUser user, String project, String filename) {
        HttpResponse<String> response = upload(user, project, filename);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json(response, "$.id");
    }

    private HttpResponse<String> upload(TestUser user, String project, String filename) {
        return upload(user, project, filename, pdfBytes());
    }

    /** A multipart/form-data upload (TestHttp only sends JSON). */
    private HttpResponse<String> upload(TestUser user, String project, String filename, byte[] content) {
        String boundary = "----flowpilot" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(content);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + docs(project)))
                .header("Authorization", "Bearer " + user.token())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    /** Starts with the PDF signature (all the upload checks); the random part avoids duplicate detection. */
    private static byte[] pdfBytes() {
        return ("%PDF-1.4\n% " + UUID.randomUUID() + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void assertNotFound(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("secret");
    }

    private static Path createStorageDirectory() {
        try {
            return Files.createTempDirectory("flowpilot-documents-it");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
