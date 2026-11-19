package com.flowpilot.ai.internal.gateway;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;

import com.flowpilot.ai.internal.AiProperties;
import com.flowpilot.ai.internal.gateway.AiExceptions.AiBusyException;
import com.flowpilot.ai.internal.gateway.AiExceptions.AiTimeoutException;
import com.flowpilot.ai.internal.gateway.AiExceptions.AiUnavailableException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * The only door to the chat model. It enforces the limits from the architecture document:
 * at most {@code maxConcurrentCalls} calls on this replica (a bulkhead, because one local GPU
 * serves one or two requests at a time), a short wait for a free slot, and a hard timeout per
 * call. Callers never hold a database transaction while this runs.
 *
 * <p>Provider-specific code (Ollama options) lives here only; the rest of the module sees plain
 * Spring AI messages, so switching providers is a configuration and gateway change.
 */
@Component
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);
    /** Some models write their reasoning between these tags; it is never part of the answer. */
    private static final Pattern THINKING = Pattern.compile("(?s)<think>.*?</think>");
    private static final Duration RETRY_WHEN_BUSY = Duration.ofSeconds(5);
    private static final Duration RETRY_WHEN_DOWN = Duration.ofSeconds(30);

    private final ChatModel chatModel;
    private final Duration queueWait;
    private final Duration callTimeout;
    private final int maxOutputTokens;
    private final Semaphore slots;
    private final ExecutorService executor;
    private final MeterRegistry metrics;

    public AiGateway(ChatModel chatModel, AiProperties properties, MeterRegistry metrics) {
        this.chatModel = chatModel;
        this.queueWait = properties.queueWait();
        this.callTimeout = properties.callTimeout();
        this.maxOutputTokens = properties.maxOutputTokens();
        this.slots = new Semaphore(properties.maxConcurrentCalls(), true);
        this.executor = Executors.newFixedThreadPool(properties.maxConcurrentCalls(), namedThreads());
        this.metrics = metrics;
    }

    /**
     * Sends the conversation to the model and returns its answer.
     *
     * @param jsonSchema when not null, the model is constrained to reply with JSON matching it
     * @throws AiBusyException        503 when no slot frees up within the queue wait
     * @throws AiTimeoutException     504 when the call exceeds the timeout
     * @throws AiUnavailableException 503 when the model cannot be reached or fails
     */
    public ModelReply complete(List<Message> messages, String jsonSchema) {
        acquireSlot();
        long start = System.nanoTime();
        Prompt prompt = new Prompt(messages, options(jsonSchema));
        Future<ChatResponse> call;
        try {
            call = executor.submit(() -> {
                try {
                    return chatModel.call(prompt);
                } finally {
                    // released when the call really ends, so a timed-out call still occupies its slot
                    slots.release();
                }
            });
        } catch (RejectedExecutionException e) {
            slots.release();
            throw unavailable(start, "rejected");
        }
        try {
            ChatResponse response = call.get(callTimeout.toMillis(), TimeUnit.MILLISECONDS);
            ModelReply reply = toReply(response, elapsedMs(start));
            record("ok", start);
            countTokens(reply);
            return reply;
        } catch (TimeoutException e) {
            call.cancel(true);
            record("timeout", start);
            throw new AiTimeoutException();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            // only the type: messages may contain prompt text, which must never be logged
            log.warn("AI model call failed: {}", cause.getClass().getSimpleName());
            throw unavailable(start, "error");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.cancel(true);
            throw unavailable(start, "interrupted");
        }
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(queueWait.toMillis(), TimeUnit.MILLISECONDS)) {
                metrics.counter("flowpilot.ai.requests.rejected").increment();
                throw new AiBusyException(RETRY_WHEN_BUSY);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiBusyException(RETRY_WHEN_BUSY);
        }
    }

    /**
     * Per-call options built ON TOP of the configured defaults (model, temperature, keep-alive from
     * spring.ai.ollama.chat.*). Starting from an empty builder would silently replace the configured
     * model with the library's own default.
     */
    private OllamaChatOptions options(String jsonSchema) {
        var builder = chatModel.getDefaultOptions() instanceof OllamaChatOptions defaults
                ? defaults.mutate() : OllamaChatOptions.builder();
        builder.numPredict(maxOutputTokens);
        if (jsonSchema != null) {
            builder.outputSchema(jsonSchema);
        }
        return builder.build();
    }

    private static ModelReply toReply(ChatResponse response, int latencyMs) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return new ModelReply("", null, null, null, latencyMs);
        }
        String text = response.getResult().getOutput().getText();
        text = text == null ? "" : THINKING.matcher(text).replaceAll("").strip();
        var metadata = response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        return new ModelReply(text,
                metadata == null ? null : metadata.getModel(),
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                latencyMs);
    }

    private AiUnavailableException unavailable(long start, String outcome) {
        record(outcome, start);
        return new AiUnavailableException(RETRY_WHEN_DOWN);
    }

    private void record(String outcome, long start) {
        Timer.builder("flowpilot.ai.requests").tag("outcome", outcome).register(metrics)
                .record(Duration.ofNanos(System.nanoTime() - start));
    }

    private void countTokens(ModelReply reply) {
        if (reply.promptTokens() != null) {
            Counter.builder("flowpilot.ai.tokens").tag("type", "prompt").register(metrics).increment(reply.promptTokens());
        }
        if (reply.completionTokens() != null) {
            Counter.builder("flowpilot.ai.tokens").tag("type", "completion").register(metrics)
                    .increment(reply.completionTokens());
        }
    }

    private static int elapsedMs(long start) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - start) / 1_000_000);
    }

    private static ThreadFactory namedThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "ai-call-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
