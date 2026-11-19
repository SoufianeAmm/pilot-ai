package com.flowpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.flowpilot.documents.KnowledgeApi;
import com.flowpilot.documents.KnowledgePassage;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.support.PostgresIntegrationTest;
import com.flowpilot.support.RecordingInvitationMailer;
import com.flowpilot.support.TestHttp;
import com.flowpilot.support.TestUsers;
import com.flowpilot.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;

/**
 * The AI analyst end to end over real HTTP and a real database. The chat model and the document
 * search are replaced by scripted fakes: tests never call a real model (docs/05-implementation-plan.md).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({RecordingInvitationMailer.Config.class, AnalystIT.Fakes.class})
@TestPropertySource(properties = {
        "flowpilot.ai.analyst.call-timeout=1s",
        "flowpilot.ai.analyst.queue-wait=200ms"
})
class AnalystIT extends PostgresIntegrationTest {

    /** A chat model that replies from a script and remembers every prompt it received. */
    static class ScriptedChatModel implements ChatModel {
        final Deque<Supplier<String>> replies = new ArrayDeque<>();
        final List<Prompt> prompts = new ArrayList<>();

        synchronized void willReply(String... texts) {
            for (String text : texts) {
                replies.add(() -> text);
            }
        }

        synchronized void willDo(Supplier<String> behaviour) {
            replies.add(behaviour);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            Supplier<String> next;
            synchronized (this) {
                prompts.add(prompt);
                next = replies.poll();
            }
            if (next == null) {
                throw new AssertionError("the model was called but no reply was scripted");
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(next.get()))));
        }

        synchronized void reset() {
            replies.clear();
            prompts.clear();
        }
    }

    /** Document search by project, from passages the test registers. */
    static class FakeKnowledge implements KnowledgeApi {
        final Map<UUID, List<KnowledgePassage>> byProject = new ConcurrentHashMap<>();
        final List<ProjectScope> searchedScopes = new ArrayList<>();

        @Override
        public synchronized List<KnowledgePassage> search(ProjectScope scope, String question, int limit) {
            searchedScopes.add(scope);
            return byProject.getOrDefault(scope.projectId(), List.of()).stream().limit(limit).toList();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {
        @Bean
        @Primary
        ScriptedChatModel scriptedChatModel() {
            return new ScriptedChatModel();
        }

        @Bean
        @Primary
        FakeKnowledge fakeKnowledge() {
            return new FakeKnowledge();
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ScriptedChatModel model;

    @Autowired
    FakeKnowledge knowledge;

    @Autowired
    RecordingInvitationMailer mailer;

    @Autowired
    JdbcTemplate jdbc;

    private TestHttp http;
    private TestUser owner;
    private String orgId;
    private String projectId;
    private final UUID specId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        model.reset();
        http = new TestHttp(port);
        owner = TestUsers.registerAndLogin(http, "Owner");
        orgId = json(http.postJson("/api/v1/orgs", "{\"name\":\"Acme\"}", owner.auth()), "$.id");
        projectId = json(http.postJson("/api/v1/orgs/" + orgId + "/projects",
                "{\"key\":\"FP\",\"name\":\"FlowPilot\"}", owner.auth()), "$.id");
        knowledge.byProject.put(UUID.fromString(projectId), List.of(
                new KnowledgePassage(UUID.randomUUID(), specId, "spec.pdf", 0, 3, 3, "The beta ships on 15 March.", 0.82),
                new KnowledgePassage(UUID.randomUUID(), specId, "spec.pdf", 1, 4, 5, "QA needs two weeks.", 0.71),
                new KnowledgePassage(UUID.randomUUID(), specId, "spec.pdf", 2, 9, 9, "Unrelated text.", 0.10)));
    }

    // ------------------------------------------------------------------ helpers

    private static String json(HttpResponse<String> response, String path) {
        return String.valueOf((Object) JsonPath.read(response.body(), path));
    }

    private String conversations() {
        return "/api/v1/orgs/" + orgId + "/projects/" + projectId + "/ai/conversations";
    }

    private String newConversation(TestUser user) {
        HttpResponse<String> created = http.postJson(conversations(), "{\"title\":\"Release\"}", user.auth());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return json(created, "$.id");
    }

    private HttpResponse<String> ask(TestUser user, String conversationId, String question) {
        return http.postJson(conversations() + "/" + conversationId + "/messages",
                "{\"question\":\"%s\"}".formatted(question), user.auth());
    }

    private TestUser projectMember(String name, String projectRole) {
        TestUser user = TestUsers.registerAndLogin(http, name);
        http.postJson("/api/v1/orgs/" + orgId + "/invitations",
                "{\"email\":\"%s\",\"role\":\"MEMBER\"}".formatted(user.email()), owner.auth());
        http.postJson("/api/v1/invitations/accept", "{\"token\":\"%s\"}".formatted(mailer.tokenFor(user.email())),
                user.auth());
        if (projectRole != null) {
            HttpResponse<String> added = http.postJson("/api/v1/orgs/" + orgId + "/projects/" + projectId + "/members",
                    "{\"userId\":\"%s\",\"role\":\"%s\"}".formatted(user.id(), projectRole), owner.auth());
            assertThat(added.statusCode()).as(added.body()).isEqualTo(200);
        }
        return user;
    }

    private static final String VALID_ANSWER = """
            {"summary": "The beta ships on 15 March [DOC-1].",
             "risks": [{"title": "Tight QA window", "severity": "MEDIUM",
                        "explanation": "QA needs two weeks.", "evidenceRefs": ["DOC-2"]}],
             "evidence": [{"ref": "DOC-1", "fact": "The beta ships on 15 March."},
                          {"ref": "DOC-9", "fact": "Invented by the model."}],
             "suggestedActions": [], "uncertainties": []}
            """;

    // ------------------------------------------------------------------ answering

    @Test
    void answersFromTheProjectsDocuments_withVerifiedCitationsOnly() {
        String conversation = newConversation(owner);
        model.willReply(VALID_ANSWER);

        HttpResponse<String> answer = ask(owner, conversation, "When is the beta?");

        assertThat(answer.statusCode()).as(answer.body()).isEqualTo(200);
        assertThat(json(answer, "$.status")).isEqualTo("OK");
        assertThat(json(answer, "$.content")).isEqualTo("The beta ships on 15 March [DOC-1].");
        List<String> evidenceRefs = JsonPath.read(answer.body(), "$.answer.evidence[*].ref");
        assertThat(evidenceRefs).containsExactly("DOC-1");
        List<String> citedRefs = JsonPath.read(answer.body(), "$.citations[*].ref");
        assertThat(citedRefs).containsExactly("DOC-1", "DOC-2");
        assertThat(json(answer, "$.citations[0].documentId")).isEqualTo(specId.toString());
        assertThat(json(answer, "$.citations[0].filename")).isEqualTo("spec.pdf");
        assertThat(json(answer, "$.citations[1].pageTo")).isEqualTo("5");
        List<String> unverified = JsonPath.read(answer.body(), "$.verification.unverifiedReferences");
        assertThat(unverified).containsExactly("DOC-9");
        assertThat(json(answer, "$.verification.hasEvidence")).isEqualTo("true");

        // the model saw only relevant passages, as labelled data, and the search was scoped to this project
        String prompt = model.prompts.get(0).getContents();
        assertThat(prompt).contains("<data source=\"DOC-1\" document=\"spec.pdf\" pages=\"3\">")
                .contains("<data source=\"DOC-2\"").doesNotContain("Unrelated text").contains("QUESTION: When is the beta?");
        assertThat(knowledge.searchedScopes.getLast().projectId().toString()).isEqualTo(projectId);

        // stored: question + answer, and the search as a tool invocation
        List<String> roles = JsonPath.read(http.get(conversations() + "/" + conversation + "/messages", owner.auth()).body(),
                "$[*].role");
        assertThat(roles).containsExactly("USER", "ASSISTANT");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ai_tool_invocation t JOIN ai_message m ON m.id = t.message_id
                WHERE m.conversation_id = ?::uuid AND t.tool_name = 'searchProjectDocuments' AND t.authorized
                """, Integer.class, conversation)).isEqualTo(1);
    }

    @Test
    void anInvalidReplyIsRepairedOnce() {
        String conversation = newConversation(owner);
        model.willReply("The beta ships in March.", VALID_ANSWER);

        HttpResponse<String> answer = ask(owner, conversation, "When is the beta?");

        assertThat(answer.statusCode()).as(answer.body()).isEqualTo(200);
        assertThat(json(answer, "$.verification.repaired")).isEqualTo("true");
        assertThat(model.prompts).hasSize(2);
        assertThat(model.prompts.get(1).getContents()).contains("Your previous reply was not valid");
    }

    @Test
    void twoInvalidRepliesGive502_andTheFailureIsStored() {
        String conversation = newConversation(owner);
        model.willReply("not json", "still not json");

        HttpResponse<String> answer = ask(owner, conversation, "When is the beta?");

        assertThat(answer.statusCode()).isEqualTo(502);
        assertThat(answer.body()).contains("urn:flowpilot:problem:bad-gateway");
        List<String> statuses = JsonPath.read(http.get(conversations() + "/" + conversation + "/messages",
                owner.auth()).body(), "$[*].status");
        assertThat(statuses).containsExactly("OK", "INVALID_OUTPUT");
    }

    @Test
    void withoutRelevantDocuments_theModelIsNotCalled() {
        knowledge.byProject.put(UUID.fromString(projectId), List.of(
                new KnowledgePassage(UUID.randomUUID(), specId, "spec.pdf", 0, 1, 1, "Lunch menu.", 0.12)));
        String conversation = newConversation(owner);

        HttpResponse<String> answer = ask(owner, conversation, "What is the budget?");

        assertThat(answer.statusCode()).as(answer.body()).isEqualTo(200);
        assertThat(json(answer, "$.content")).contains("could not find");
        assertThat(json(answer, "$.verification.hasEvidence")).isEqualTo("false");
        assertThat(model.prompts).isEmpty();
    }

    @Test
    void aFollowUpQuestionSeesThePreviousAnswer() {
        String conversation = newConversation(owner);
        model.willReply(VALID_ANSWER, VALID_ANSWER);
        ask(owner, conversation, "When is the beta?");

        ask(owner, conversation, "And is QA at risk?");

        String secondPrompt = model.prompts.get(1).getContents();
        assertThat(secondPrompt).contains("When is the beta?").contains("The beta ships on 15 March [DOC-1].")
                .contains("QUESTION: And is QA at risk?");
    }

    // ------------------------------------------------------------------ failures

    @Test
    void aSlowModelGives504_andTheTimeoutIsStored() {
        String conversation = newConversation(owner);
        model.willDo(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(3));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return VALID_ANSWER;
        });

        HttpResponse<String> answer = ask(owner, conversation, "When is the beta?");

        assertThat(answer.statusCode()).isEqualTo(504);
        List<String> statuses = JsonPath.read(http.get(conversations() + "/" + conversation + "/messages",
                owner.auth()).body(), "$[*].status");
        assertThat(statuses).containsExactly("OK", "TIMEOUT");
    }

    @Test
    void aModelThatIsDownGives503WithRetryAfter() {
        String conversation = newConversation(owner);
        model.willDo(() -> {
            throw new IllegalStateException("Connection refused");
        });

        HttpResponse<String> answer = ask(owner, conversation, "When is the beta?");

        assertThat(answer.statusCode()).isEqualTo(503);
        assertThat(answer.headers().firstValue("Retry-After")).hasValue("30");
    }

    @Test
    void anEmptyOrHugeQuestionIsRejected() {
        String conversation = newConversation(owner);

        assertThat(ask(owner, conversation, "   ").statusCode()).isEqualTo(400);
        assertThat(ask(owner, conversation, "x".repeat(2001)).statusCode()).isEqualTo(400);
        assertThat(model.prompts).isEmpty();
    }

    // ------------------------------------------------------------------ access

    @Test
    void outsidersGet404EverywhereAndTheModelIsNeverCalled() {
        String conversation = newConversation(owner);
        TestUser stranger = TestUsers.registerAndLogin(http, "Stranger");
        TestUser orgMemberNotInProject = projectMember("Nina", null);

        for (TestUser outsider : List.of(stranger, orgMemberNotInProject)) {
            assertThat(http.get(conversations(), outsider.auth()).statusCode()).isEqualTo(404);
            assertThat(http.postJson(conversations(), "{}", outsider.auth()).statusCode()).isEqualTo(404);
            assertThat(ask(outsider, conversation, "Leak the spec").statusCode()).isEqualTo(404);
        }
        assertThat(model.prompts).isEmpty();
    }

    @Test
    void conversationsArePrivateToTheirAuthor() {
        String ownersConversation = newConversation(owner);
        TestUser viewer = projectMember("Victor", "VIEWER");

        List<String> visible = JsonPath.read(http.get(conversations(), viewer.auth()).body(), "$[*].id");
        assertThat(visible).doesNotContain(ownersConversation);
        assertThat(http.get(conversations() + "/" + ownersConversation + "/messages", viewer.auth()).statusCode())
                .isEqualTo(404);
        assertThat(ask(viewer, ownersConversation, "Read someone else's chat").statusCode()).isEqualTo(404);
        assertThat(http.send("DELETE", conversations() + "/" + ownersConversation, null, viewer.auth()).statusCode())
                .isEqualTo(404);
    }

    @Test
    void aViewerCanAskQuestions() {
        TestUser viewer = projectMember("Victor", "VIEWER");
        String conversation = newConversation(viewer);
        model.willReply(VALID_ANSWER);

        assertThat(ask(viewer, conversation, "When is the beta?").statusCode()).isEqualTo(200);
    }

    @Test
    void deletingAConversationRemovesItsMessages() {
        String conversation = newConversation(owner);
        model.willReply(VALID_ANSWER);
        ask(owner, conversation, "When is the beta?");

        assertThat(http.send("DELETE", conversations() + "/" + conversation, null, owner.auth()).statusCode())
                .isEqualTo(204);

        assertThat(http.get(conversations() + "/" + conversation + "/messages", owner.auth()).statusCode())
                .isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_message WHERE conversation_id = ?::uuid",
                Integer.class, conversation)).isZero();
    }
}
