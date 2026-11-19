package com.flowpilot.ai.internal.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Component;

import com.flowpilot.ai.internal.application.AnalystResponse.Evidence;
import com.flowpilot.ai.internal.application.AnalystResponse.Risk;
import com.flowpilot.ai.internal.application.AnalystResponse.SuggestedAction;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the model's raw text into a trusted answer: it must be JSON of the {@link AnalystResponse}
 * shape, within the Bean Validation limits, and every reference it cites must be in the
 * {@link EvidenceLedger}. Unknown references are removed and reported, never shown as sources.
 */
@Component
class AnswerValidator {

    /** The model's output cannot be used; {@link #getMessage()} says why, and goes back to the model once. */
    static class InvalidAnswerException extends Exception {
        InvalidAnswerException(String reason) {
            super(reason);
        }
    }

    /**
     * @param answer         the answer with only verified references left
     * @param citedRefs      the verified references the answer uses, in order of first use
     * @param unverifiedRefs references the model cited that it was never given (removed)
     */
    record ValidatedAnswer(AnalystResponse answer, List<String> citedRefs, List<String> unverifiedRefs) {
    }

    private final JsonMapper json;
    private final Validator validator;

    AnswerValidator(JsonMapper json, Validator validator) {
        this.json = json;
        this.validator = validator;
    }

    ValidatedAnswer validate(String text, EvidenceLedger ledger) throws InvalidAnswerException {
        AnalystResponse parsed;
        try {
            parsed = json.readValue(stripCodeFence(text), AnalystResponse.class);
        } catch (JacksonException e) {
            throw new InvalidAnswerException("the reply is not a JSON object of the required shape");
        }
        if (parsed == null) {
            throw new InvalidAnswerException("the reply is empty");
        }
        AnalystResponse answer = parsed.normalized();
        Set<ConstraintViolation<AnalystResponse>> violations = validator.validate(answer);
        if (!violations.isEmpty()) {
            throw new InvalidAnswerException(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
        return verifyReferences(answer, ledger);
    }

    private static ValidatedAnswer verifyReferences(AnalystResponse answer, EvidenceLedger ledger) {
        Set<String> cited = new LinkedHashSet<>();
        Set<String> unverified = new LinkedHashSet<>();

        List<Evidence> evidence = new ArrayList<>();
        for (Evidence item : answer.evidence()) {
            Optional<String> ref = ledger.resolve(item.ref());
            if (ref.isPresent()) {
                cited.add(ref.get());
                evidence.add(new Evidence(ref.get(), item.fact()));
            } else {
                unverified.add(item.ref().strip());
            }
        }
        List<Risk> risks = answer.risks().stream()
                .map(r -> new Risk(r.title(), r.severity(), r.explanation(), keepKnown(r.evidenceRefs(), ledger, cited, unverified)))
                .toList();
        List<SuggestedAction> actions = answer.suggestedActions().stream()
                .map(a -> new SuggestedAction(a.action(), a.rationale(), keepKnown(a.relatedRefs(), ledger, cited, unverified)))
                .toList();

        AnalystResponse cleaned = new AnalystResponse(answer.summary(), risks, evidence, actions, answer.uncertainties());
        return new ValidatedAnswer(cleaned, List.copyOf(cited), List.copyOf(unverified));
    }

    private static List<String> keepKnown(List<String> refs, EvidenceLedger ledger, Set<String> cited,
                                          Set<String> unverified) {
        List<String> kept = new ArrayList<>();
        for (String ref : refs) {
            Optional<String> known = ledger.resolve(ref);
            if (known.isPresent()) {
                cited.add(known.get());
                if (!kept.contains(known.get())) {
                    kept.add(known.get());
                }
            } else if (ref != null && !ref.isBlank()) {
                unverified.add(ref.strip());
            }
        }
        return kept;
    }

    /** Some models wrap JSON in a markdown fence even when told not to. */
    private static String stripCodeFence(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }
}
