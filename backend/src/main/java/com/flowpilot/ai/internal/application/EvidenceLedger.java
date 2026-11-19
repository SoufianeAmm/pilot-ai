package com.flowpilot.ai.internal.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.flowpilot.documents.KnowledgePassage;

/**
 * Everything the model was shown for one answer, by reference. After the model answers, every
 * reference it cites must be in here; anything else was invented and is removed. One ledger per
 * request, never shared.
 *
 * <p>Document passages get short per-answer labels ({@code DOC-1}, {@code DOC-2}…) instead of
 * long ids: a small model copies "DOC-2" reliably, a UUID much less so. The ledger maps each label
 * back to the real document and pages, which is what the user sees and can open.
 */
public final class EvidenceLedger {

    /** Where a cited reference points to, so the UI can show and open it. */
    public record Citation(String ref, UUID documentId, String filename, Integer pageFrom, Integer pageTo) {
    }

    /** A passage given to the model, with its label. */
    record LabeledPassage(String ref, KnowledgePassage passage) {
    }

    private final Map<String, LabeledPassage> passages = new LinkedHashMap<>();

    /** Registers a passage and returns its label. */
    String addPassage(KnowledgePassage passage) {
        String ref = "DOC-" + (passages.size() + 1);
        passages.put(ref, new LabeledPassage(ref, passage));
        return ref;
    }

    Collection<LabeledPassage> passages() {
        return passages.values();
    }

    boolean isEmpty() {
        return passages.isEmpty();
    }

    int size() {
        return passages.size();
    }

    /** The ledger's spelling of a reference written by the model ("[doc-2]" → "DOC-2"), if it exists. */
    Optional<String> resolve(String ref) {
        if (ref == null) {
            return Optional.empty();
        }
        String normalized = ref.strip().replaceAll("^[\\[(]+|[\\])]+$", "").strip().toUpperCase(Locale.ROOT);
        return passages.containsKey(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    List<Citation> citations(Collection<String> refs) {
        List<Citation> citations = new ArrayList<>();
        for (String ref : refs) {
            LabeledPassage labeled = passages.get(ref);
            if (labeled != null) {
                KnowledgePassage p = labeled.passage();
                citations.add(new Citation(ref, p.documentId(), p.filename(), p.pageFrom(), p.pageTo()));
            }
        }
        return citations;
    }
}
