package com.flowpilot.ai.internal.application;

/**
 * JSON Schema of {@link AnalystResponse}, given to the model as its output format. With Ollama the
 * model's decoding is constrained to it, which matters for small local models that otherwise
 * produce broken JSON. Keep it in sync with the record (PromptBuilderTest checks that).
 *
 * <p>The ORDER matters: a model writes JSON keys in schema order, so "evidence" comes first and
 * "summary" last. Measured on qwen3:4b-instruct (2026-11-19): with the summary first, the model
 * committed to "not specified" before reading the facts, and once looped until its token limit;
 * with evidence first, every test question was answered correctly and in valid JSON.
 */
final class ResponseSchema {

    static final String JSON = """
            {
              "type": "object",
              "properties": {
                "evidence": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "ref": {"type": "string"},
                      "fact": {"type": "string"}
                    },
                    "required": ["ref", "fact"]
                  }
                },
                "risks": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "title": {"type": "string"},
                      "severity": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"]},
                      "explanation": {"type": "string"},
                      "evidenceRefs": {"type": "array", "items": {"type": "string"}}
                    },
                    "required": ["title", "severity", "explanation", "evidenceRefs"]
                  }
                },
                "suggestedActions": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "action": {"type": "string"},
                      "rationale": {"type": "string"},
                      "relatedRefs": {"type": "array", "items": {"type": "string"}}
                    },
                    "required": ["action", "rationale", "relatedRefs"]
                  }
                },
                "uncertainties": {"type": "array", "items": {"type": "string"}},
                "summary": {"type": "string"}
              },
              "required": ["evidence", "risks", "suggestedActions", "uncertainties", "summary"]
            }
            """;

    private ResponseSchema() {
    }
}
