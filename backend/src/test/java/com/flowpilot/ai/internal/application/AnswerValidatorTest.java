package com.flowpilot.ai.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import com.flowpilot.ai.internal.application.AnswerValidator.InvalidAnswerException;
import com.flowpilot.ai.internal.application.AnswerValidator.ValidatedAnswer;
import com.flowpilot.documents.KnowledgePassage;

import tools.jackson.databind.json.JsonMapper;

class AnswerValidatorTest {

    private final AnswerValidator validator = new AnswerValidator(JsonMapper.builder().build(),
            Validation.buildDefaultValidatorFactory().getValidator());

    private static EvidenceLedger ledgerWith(int passages) {
        EvidenceLedger ledger = new EvidenceLedger();
        for (int i = 0; i < passages; i++) {
            ledger.addPassage(new KnowledgePassage(UUID.randomUUID(), UUID.randomUUID(), "spec.pdf", i, i + 1, i + 1,
                    "text " + i, 0.9));
        }
        return ledger;
    }

    @Test
    void keepsReferencesTheModelWasGiven_andRemovesInventedOnes() throws Exception {
        String reply = """
                {"summary": "Beta ships in March [DOC-1].",
                 "risks": [{"title": "Late QA", "severity": "HIGH", "explanation": "QA starts late",
                            "evidenceRefs": ["DOC-2", "TASK:FP-99"]}],
                 "evidence": [{"ref": "DOC-1", "fact": "Beta release on 15 March"},
                              {"ref": "DOC-7", "fact": "made up"}],
                 "suggestedActions": [{"action": "Start QA earlier", "rationale": "QA is late",
                                       "relatedRefs": ["[doc-2]"]}],
                 "uncertainties": []}
                """;

        ValidatedAnswer result = validator.validate(reply, ledgerWith(2));

        assertThat(result.answer().evidence()).extracting(AnalystResponse.Evidence::ref).containsExactly("DOC-1");
        assertThat(result.answer().risks().get(0).evidenceRefs()).containsExactly("DOC-2");
        assertThat(result.answer().suggestedActions().get(0).relatedRefs()).containsExactly("DOC-2");
        assertThat(result.citedRefs()).containsExactly("DOC-1", "DOC-2");
        assertThat(result.unverifiedRefs()).containsExactly("DOC-7", "TASK:FP-99");
    }

    @Test
    void acceptsMissingListsAndAMarkdownFence() throws Exception {
        ValidatedAnswer result = validator.validate("```json\n{\"summary\": \"Nothing found.\"}\n```", ledgerWith(1));

        assertThat(result.answer().summary()).isEqualTo("Nothing found.");
        assertThat(result.answer().risks()).isEmpty();
        assertThat(result.answer().evidence()).isEmpty();
        assertThat(result.citedRefs()).isEmpty();
    }

    @Test
    void rejectsTextThatIsNotJson() {
        assertThatThrownBy(() -> validator.validate("Beta ships in March.", ledgerWith(1)))
                .isInstanceOf(InvalidAnswerException.class)
                .hasMessageContaining("not a JSON object");
    }

    @Test
    void rejectsAnAnswerWithoutSummary_withAReasonTheModelCanFix() {
        assertThatThrownBy(() -> validator.validate("{\"summary\": \"  \", \"evidence\": []}", ledgerWith(1)))
                .isInstanceOf(InvalidAnswerException.class)
                .hasMessageContaining("summary");
    }

    @Test
    void rejectsAnUnknownSeverity() {
        String reply = """
                {"summary": "s", "risks": [{"title": "t", "severity": "CATASTROPHIC", "explanation": "e"}]}
                """;
        assertThatThrownBy(() -> validator.validate(reply, ledgerWith(1))).isInstanceOf(InvalidAnswerException.class);
    }
}
