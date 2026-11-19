package com.flowpilot.ai.internal.application;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The structured answer the model must produce (docs/01-architecture.md section 7). The model is
 * constrained to this shape by {@link ResponseSchema}; Bean Validation then checks the limits, and
 * the evidence ledger checks every reference.
 *
 * @param summary          the answer in a few sentences
 * @param risks            problems the data shows, each backed by references
 * @param evidence         the facts used, each with the reference of its source
 * @param suggestedActions what the user could do next (never executed by the AI)
 * @param uncertainties    what the data does not say
 */
public record AnalystResponse(
        @NotBlank @Size(max = 1500) String summary,
        @Valid @Size(max = 10) List<Risk> risks,
        @Valid @Size(max = 20) List<Evidence> evidence,
        @Valid @Size(max = 10) List<SuggestedAction> suggestedActions,
        @Size(max = 10) List<@NotBlank @Size(max = 500) String> uncertainties) {

    public enum Severity { LOW, MEDIUM, HIGH }

    public record Risk(@NotBlank @Size(max = 200) String title,
                       @NotNull Severity severity,
                       @NotBlank @Size(max = 1000) String explanation,
                       @Size(max = 20) List<String> evidenceRefs) {
    }

    public record Evidence(@NotBlank @Size(max = 100) String ref, @NotBlank @Size(max = 1000) String fact) {
    }

    public record SuggestedAction(@NotBlank @Size(max = 300) String action,
                                  @Size(max = 1000) String rationale,
                                  @Size(max = 20) List<String> relatedRefs) {
    }

    /** The same answer with every missing list replaced by an empty one. */
    AnalystResponse normalized() {
        return new AnalystResponse(summary,
                orEmpty(risks).stream().map(r -> new Risk(r.title(), r.severity(), r.explanation(),
                        orEmpty(r.evidenceRefs()))).toList(),
                orEmpty(evidence),
                orEmpty(suggestedActions).stream().map(a -> new SuggestedAction(a.action(), a.rationale(),
                        orEmpty(a.relatedRefs()))).toList(),
                orEmpty(uncertainties));
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list.stream().filter(java.util.Objects::nonNull).toList();
    }
}
