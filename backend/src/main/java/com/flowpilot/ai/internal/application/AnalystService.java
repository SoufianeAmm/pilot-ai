package com.flowpilot.ai.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import com.flowpilot.ai.internal.AiProperties;
import com.flowpilot.ai.internal.application.AnswerValidator.InvalidAnswerException;
import com.flowpilot.ai.internal.application.AnswerValidator.ValidatedAnswer;
import com.flowpilot.ai.internal.application.EvidenceLedger.LabeledPassage;
import com.flowpilot.ai.internal.application.MessageView.StoredAnswer;
import com.flowpilot.ai.internal.application.MessageView.Verification;
import com.flowpilot.ai.internal.application.PromptBuilder.Turn;
import com.flowpilot.ai.internal.gateway.AiExceptions.AiInvalidOutputException;
import com.flowpilot.ai.internal.gateway.AiExceptions.AiTimeoutException;
import com.flowpilot.ai.internal.gateway.AiExceptions.AiUnavailableException;
import com.flowpilot.ai.internal.gateway.AiGateway;
import com.flowpilot.ai.internal.gateway.ModelReply;
import com.flowpilot.ai.internal.persistence.ConversationStore;
import com.flowpilot.ai.internal.persistence.ConversationStore.Exchange;
import com.flowpilot.ai.internal.persistence.ConversationStore.StoredMessage;
import com.flowpilot.ai.internal.persistence.ConversationStore.ToolCall;
import com.flowpilot.documents.KnowledgeApi;
import com.flowpilot.documents.KnowledgePassage;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.shared.web.ApiException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Answers a question about a project (backlog D1, D3, D5, E3), following docs/01-architecture.md
 * section 7:
 * <ol>
 *   <li>the caller holds a {@link ProjectScope} with AI_ASK; the project is never chosen by the model;</li>
 *   <li>the project's documents are searched through the documents module ({@link KnowledgeApi}),
 *       restricted to that scope, and every passage is registered in an {@link EvidenceLedger};</li>
 *   <li>the model answers in the {@link AnalystResponse} JSON shape through the {@link AiGateway};
 *       an invalid reply gets exactly one repair attempt;</li>
 *   <li>references not in the ledger are removed and reported (unverifiedReferences);</li>
 *   <li>the question, the answer and the search are stored together.</li>
 * </ol>
 * Deliberately not transactional: no database connection is held while the model thinks.
 */
@Service
public class AnalystService {

    private static final Logger log = LoggerFactory.getLogger(AnalystService.class);

    static final String SEARCH_TOOL = "searchProjectDocuments";
    static final String NOT_FOUND = "I could not find information about this in the project's documents.";
    static final String TIMEOUT_TEXT = "The AI analyst took too long to answer this question.";
    static final String ERROR_TEXT = "The AI analyst was unavailable when this question was asked.";
    static final String INVALID_TEXT = "The AI analyst could not produce a valid answer to this question.";

    private final ConversationService conversations;
    private final ConversationStore store;
    private final KnowledgeApi knowledge;
    private final AiGateway gateway;
    private final AnswerValidator validator;
    private final JsonMapper json;
    private final AiProperties properties;
    private final Clock clock;

    AnalystService(ConversationService conversations, ConversationStore store, KnowledgeApi knowledge,
                   AiGateway gateway, AnswerValidator validator, JsonMapper json, AiProperties properties,
                   Clock clock) {
        this.conversations = conversations;
        this.store = store;
        this.knowledge = knowledge;
        this.gateway = gateway;
        this.validator = validator;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    public MessageView ask(ProjectScope scope, UUID conversationId, String rawQuestion) {
        scope.require(ProjectPermission.AI_ASK);
        String question = requireQuestion(rawQuestion);
        conversations.requireOwn(scope, conversationId);
        Instant askedAt = clock.instant();
        List<Turn> history = history(scope, conversationId);

        EvidenceLedger ledger = new EvidenceLedger();
        ToolCall search = searchDocuments(scope, question, ledger);
        if (ledger.isEmpty()) {
            AnalystResponse answer = new AnalystResponse(NOT_FOUND, List.of(), List.of(), List.of(),
                    List.of("No document of this project matches the question."));
            Verification verification = new Verification(0, List.of(), List.of(), false, false);
            return save(scope, conversationId, new Exchange(question, askedAt, NOT_FOUND,
                    stored(answer, ledger, List.of()), write(verification), "OK", null, null, null, 0, search));
        }

        List<Message> prompt = PromptBuilder.build(history, question, ledger, properties.maxPassageChars());
        Usage usage = new Usage();
        ModelReply first = call(scope, conversationId, question, askedAt, search, prompt, usage);
        ValidatedAnswer validated;
        boolean repaired = false;
        try {
            validated = validator.validate(first.text(), ledger);
        } catch (InvalidAnswerException firstProblem) {
            log.info("AI answer invalid, asking the model to repair it: {}", firstProblem.getMessage());
            ModelReply second = call(scope, conversationId, question, askedAt, search,
                    PromptBuilder.repair(prompt, first.text(), firstProblem.getMessage()), usage);
            try {
                validated = validator.validate(second.text(), ledger);
                repaired = true;
            } catch (InvalidAnswerException secondProblem) {
                save(scope, conversationId, failure(question, askedAt, INVALID_TEXT, "INVALID_OUTPUT", usage, search));
                throw new AiInvalidOutputException();
            }
        }

        Verification verification = new Verification(ledger.size(), validated.citedRefs(),
                validated.unverifiedRefs(), !validated.citedRefs().isEmpty(), repaired);
        AnalystResponse answer = validated.answer();
        return save(scope, conversationId, new Exchange(question, askedAt, answer.summary(),
                stored(answer, ledger, validated.citedRefs()), write(verification), "OK", usage.model,
                usage.promptTokens, usage.completionTokens, usage.latencyMs, search));
    }

    /** Calls the model; a timeout or outage is stored as the answer before the error goes to the client. */
    private ModelReply call(ProjectScope scope, UUID conversationId, String question, Instant askedAt,
                            ToolCall search, List<Message> prompt, Usage usage) {
        try {
            ModelReply reply = gateway.complete(prompt, ResponseSchema.JSON);
            usage.add(reply);
            return reply;
        } catch (AiTimeoutException e) {
            save(scope, conversationId, failure(question, askedAt, TIMEOUT_TEXT, "TIMEOUT", usage, search));
            throw e;
        } catch (AiUnavailableException e) {
            save(scope, conversationId, failure(question, askedAt, ERROR_TEXT, "ERROR", usage, search));
            throw e;
        }
        // AiBusyException is not stored: nothing was attempted, the client simply retries.
    }

    private ToolCall searchDocuments(ProjectScope scope, String question, EvidenceLedger ledger) {
        long start = System.nanoTime();
        List<KnowledgePassage> passages;
        try {
            passages = knowledge.search(scope, question, properties.retrievalLimit());
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            // the search needs the embedding model: if it is down, so is the analyst
            log.warn("Document search failed: {}", e.getClass().getSimpleName());
            throw new AiUnavailableException(java.time.Duration.ofSeconds(30));
        }
        passages.stream().filter(p -> p.score() >= properties.minRelevance()).forEach(ledger::addPassage);
        List<Map<String, Object>> returned = new ArrayList<>();
        for (LabeledPassage labeled : ledger.passages()) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("ref", labeled.ref());
            ref.put("documentId", labeled.passage().documentId());
            ref.put("chunkId", labeled.passage().chunkId());
            ref.put("pageFrom", labeled.passage().pageFrom());
            ref.put("pageTo", labeled.passage().pageTo());
            ref.put("score", Math.round(labeled.passage().score() * 1000) / 1000.0);
            returned.add(ref);
        }
        int durationMs = (int) ((System.nanoTime() - start) / 1_000_000);
        // the question itself is already stored as the user message; the arguments record only the limits
        return new ToolCall(SEARCH_TOOL, write(Map.of("limit", properties.retrievalLimit(),
                "minRelevance", properties.minRelevance())), write(returned), durationMs);
    }

    private List<Turn> history(ProjectScope scope, UUID conversationId) {
        List<StoredMessage> messages = store.messages(scope, conversationId);
        List<Turn> turns = new ArrayList<>();
        for (int i = 0; i + 1 < messages.size(); i++) {
            StoredMessage question = messages.get(i);
            StoredMessage answer = messages.get(i + 1);
            if ("USER".equals(question.role()) && "ASSISTANT".equals(answer.role()) && "OK".equals(answer.status())) {
                turns.add(new Turn(question.content(), answer.content()));
                i++;
            }
        }
        int keep = Math.max(0, properties.historyTurns());
        return turns.size() <= keep ? turns : turns.subList(turns.size() - keep, turns.size());
    }

    private String requireQuestion(String raw) {
        String question = raw == null ? "" : raw.strip();
        if (question.isEmpty() || question.length() > properties.maxQuestionLength()) {
            throw ApiException.badRequest("The question must be between 1 and "
                    + properties.maxQuestionLength() + " characters.");
        }
        return question;
    }

    private MessageView save(ProjectScope scope, UUID conversationId, Exchange exchange) {
        return conversations.toView(store.saveExchange(scope, conversationId, exchange));
    }

    private Exchange failure(String question, Instant askedAt, String text, String status, Usage usage, ToolCall search) {
        return new Exchange(question, askedAt, text, null, null, status, usage.model, usage.promptTokens,
                usage.completionTokens, usage.latencyMs, search);
    }

    private String stored(AnalystResponse answer, EvidenceLedger ledger, List<String> citedRefs) {
        return write(new StoredAnswer(answer, ledger.citations(citedRefs)));
    }

    private String write(Object value) {
        return json.writeValueAsString(value);
    }

    /** Model cost summed over the first call and the repair call, if any. */
    private static final class Usage {
        private String model;
        private Integer promptTokens;
        private Integer completionTokens;
        private int latencyMs;

        void add(ModelReply reply) {
            model = reply.model() != null ? reply.model() : model;
            promptTokens = sum(promptTokens, reply.promptTokens());
            completionTokens = sum(completionTokens, reply.completionTokens());
            latencyMs += reply.latencyMs();
        }

        private static Integer sum(Integer a, Integer b) {
            return a == null ? b : b == null ? a : a + b;
        }
    }
}
