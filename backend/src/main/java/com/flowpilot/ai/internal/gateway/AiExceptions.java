package com.flowpilot.ai.internal.gateway;

import java.time.Duration;

import org.springframework.http.HttpStatus;

import com.flowpilot.shared.web.ApiException;
import com.flowpilot.shared.web.ProblemTypes;
import com.flowpilot.shared.web.RetryLaterException;

/** The ways a model call can fail, each mapped to the HTTP answer the architecture specifies. */
public final class AiExceptions {

    private AiExceptions() {
    }

    /** 503 + Retry-After: every model slot on this replica is taken. */
    public static class AiBusyException extends RetryLaterException {
        public AiBusyException(Duration retryAfter) {
            super(HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.SERVICE_UNAVAILABLE, "AI busy",
                    "The AI analyst is answering other questions right now. Try again in a few seconds.", retryAfter);
        }
    }

    /** 503 + Retry-After: the model could not be reached or failed. */
    public static class AiUnavailableException extends RetryLaterException {
        public AiUnavailableException(Duration retryAfter) {
            super(HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.SERVICE_UNAVAILABLE, "AI unavailable",
                    "The AI analyst is temporarily unavailable. The rest of FlowPilot keeps working.", retryAfter);
        }
    }

    /** 504: the model did not answer within the call timeout. */
    public static class AiTimeoutException extends ApiException {
        public AiTimeoutException() {
            super(HttpStatus.GATEWAY_TIMEOUT, ProblemTypes.GATEWAY_TIMEOUT, "AI timeout",
                    "The AI analyst took too long to answer. Try a shorter or more specific question.");
        }
    }

    /** 502: the model answered twice with output that does not match the expected format. */
    public static class AiInvalidOutputException extends ApiException {
        public AiInvalidOutputException() {
            super(HttpStatus.BAD_GATEWAY, ProblemTypes.BAD_GATEWAY, "AI answer invalid",
                    "The AI analyst could not produce a valid answer. Try rephrasing the question.");
        }
    }
}
