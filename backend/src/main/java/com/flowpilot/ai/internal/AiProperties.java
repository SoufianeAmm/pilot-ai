package com.flowpilot.ai.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits of the AI analyst (flowpilot.ai.analyst.* in application.yml). The model itself and its
 * address are Spring AI settings (spring.ai.ollama.*), so the provider stays configuration.
 *
 * @param maxConcurrentCalls model calls running at once on this replica (one local GPU serves 1-2)
 * @param queueWait          how long a request waits for a free slot before answering 503
 * @param callTimeout        how long one model call may take before answering 504
 * @param maxQuestionLength  longest accepted question, in characters
 * @param retrievalLimit     document passages given to the model per question
 * @param minRelevance       passages scoring below this (0-1) are ignored as unrelated
 * @param maxPassageChars    each passage is cut to this length in the prompt
 * @param historyTurns       previous question/answer pairs sent for follow-up questions
 * @param maxOutputTokens    upper bound on the length of one answer
 */
@ConfigurationProperties("flowpilot.ai.analyst")
public record AiProperties(
        @DefaultValue("2") int maxConcurrentCalls,
        @DefaultValue("2s") Duration queueWait,
        @DefaultValue("120s") Duration callTimeout,
        @DefaultValue("2000") int maxQuestionLength,
        @DefaultValue("4") int retrievalLimit,
        @DefaultValue("0.35") double minRelevance,
        @DefaultValue("1500") int maxPassageChars,
        @DefaultValue("3") int historyTurns,
        @DefaultValue("800") int maxOutputTokens) {
}
