package com.flowpilot.ai.internal.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.flowpilot.ai.internal.AiProperties;
import com.flowpilot.ai.internal.gateway.AiExceptions.AiBusyException;
import com.flowpilot.ai.internal.gateway.AiExceptions.AiTimeoutException;
import com.flowpilot.ai.internal.gateway.AiExceptions.AiUnavailableException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AiGatewayTest {

    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    private AiGateway gateway(Function<Prompt, ChatResponse> model, int slots, Duration wait, Duration timeout) {
        ChatModel chatModel = prompt -> model.apply(prompt);
        return new AiGateway(chatModel, new AiProperties(slots, wait, timeout, 2000, 4, 0.35, 1500, 3, 800), metrics);
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void returnsTheAnswerWithoutTheModelsReasoning() {
        AiGateway gateway = gateway(p -> reply("<think>let me think</think>\n{\"summary\":\"ok\"}"), 2,
                Duration.ofSeconds(1), Duration.ofSeconds(5));

        ModelReply reply = gateway.complete(List.of(new UserMessage("hi")), null);

        assertThat(reply.text()).isEqualTo("{\"summary\":\"ok\"}");
        assertThat(metrics.get("flowpilot.ai.requests").tag("outcome", "ok").timer().count()).isEqualTo(1);
    }

    @Test
    void keepsTheConfiguredModelAndAddsTheOutputSchema() {
        java.util.concurrent.atomic.AtomicReference<Prompt> seen = new java.util.concurrent.atomic.AtomicReference<>();
        ChatModel configured = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                seen.set(prompt);
                return reply("{}");
            }

            @Override
            public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
                return org.springframework.ai.ollama.api.OllamaChatOptions.builder()
                        .model("qwen3:4b-instruct").temperature(0.2).build();
            }
        };
        AiGateway gateway = new AiGateway(configured,
                new AiProperties(2, Duration.ofSeconds(1), Duration.ofSeconds(5), 2000, 4, 0.35, 1500, 3, 800), metrics);

        gateway.complete(List.of(new UserMessage("hi")), "{\"type\":\"object\"}");

        var options = (org.springframework.ai.ollama.api.OllamaChatOptions) seen.get().getOptions();
        // regression: per-call options once replaced the configured model with the library default ("mistral")
        assertThat(options.getModel()).isEqualTo("qwen3:4b-instruct");
        assertThat(options.getTemperature()).isEqualTo(0.2);
        assertThat(options.getNumPredict()).isEqualTo(800);
        assertThat(options.getOutputSchema()).contains("\"type\"");
    }

    @Test
    void answers504WhenTheModelIsTooSlow() {
        AiGateway gateway = gateway(p -> {
            sleep(Duration.ofSeconds(2));
            return reply("late");
        }, 2, Duration.ofSeconds(1), Duration.ofMillis(200));

        assertThatThrownBy(() -> gateway.complete(List.of(new UserMessage("hi")), null))
                .isInstanceOf(AiTimeoutException.class);
    }

    @Test
    void answers503WhenTheModelFails_andLogsNoPromptText() {
        AiGateway gateway = gateway(p -> {
            throw new IllegalStateException("connection refused to secret prompt");
        }, 2, Duration.ofSeconds(1), Duration.ofSeconds(5));

        assertThatThrownBy(() -> gateway.complete(List.of(new UserMessage("hi")), null))
                .isInstanceOf(AiUnavailableException.class)
                .satisfies(e -> assertThat(((AiUnavailableException) e).retryAfter()).isPositive());
    }

    @Test
    void answers503WhenEverySlotIsTaken_andFreesTheSlotAfterwards() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AiGateway gateway = gateway(p -> {
            started.countDown();
            await(release);
            return reply("first");
        }, 1, Duration.ofMillis(100), Duration.ofSeconds(5));

        CompletableFuture<ModelReply> first = CompletableFuture.supplyAsync(
                () -> gateway.complete(List.of(new UserMessage("one")), null));
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> gateway.complete(List.of(new UserMessage("two")), null))
                .isInstanceOf(AiBusyException.class);

        release.countDown();
        assertThat(first.get(2, TimeUnit.SECONDS).text()).isEqualTo("first");
        // the slot is free again: the next question goes through
        assertThat(gateway.complete(List.of(new UserMessage("three")), null).text()).isEqualTo("first");
        assertThat(metrics.counter("flowpilot.ai.requests.rejected").count()).isEqualTo(1);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
