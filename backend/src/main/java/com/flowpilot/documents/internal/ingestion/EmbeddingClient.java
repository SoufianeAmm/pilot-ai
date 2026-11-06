package com.flowpilot.documents.internal.ingestion;

import java.util.List;

/** Turns texts into vectors. An interface, so the provider can be replaced (or faked in tests). */
interface EmbeddingClient {

    String modelName();

    List<float[]> embed(List<String> texts);
}