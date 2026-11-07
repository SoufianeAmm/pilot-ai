package com.flowpilot.documents.internal.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.flowpilot.documents.internal.domain.Document;
import com.flowpilot.documents.internal.persistence.ChunkStore;
import com.flowpilot.documents.internal.persistence.DocumentRepository;
import com.flowpilot.documents.internal.storage.DocumentStorage;

/**
 * Processes one document: extract, chunk, embed, store. The slow steps (reading the PDF, calling
 * the embedding model) run outside any database transaction, so no connection is held meanwhile.
 */
@Service
class IngestionService {

    private static final int CHUNK_MAX_CHARS = 2400;
    private static final int CHUNK_OVERLAP_CHARS = 240;

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final PdfTextExtractor extractor;
    private final EmbeddingClient embeddings;
    private final ChunkStore chunkStore;
    private final TransactionTemplate tx;
    private final Clock clock;

    IngestionService(DocumentRepository documents, DocumentStorage storage, PdfTextExtractor extractor,
                     EmbeddingClient embeddings, ChunkStore chunkStore,
                     PlatformTransactionManager transactionManager, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.extractor = extractor;
        this.embeddings = embeddings;
        this.chunkStore = chunkStore;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    void process(UUID documentId) {
        String storageKey = tx.execute(status -> {
            Document document = documents.findById(documentId).orElse(null);
            if (document == null || document.isDeleted()) {
                return null;
            }
            document.markProcessing(clock.instant());
            return document.getStorageKey();
        });
        if (storageKey == null) {
            return;   // the document was deleted in the meantime: nothing to do
        }

        PdfTextExtractor.ExtractedDocument extracted;
        try (InputStream content = storage.open(storageKey)) {
            extracted = extractor.extract(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int pageCount = extracted.pageCount();

        List<TextChunker.Chunk> chunks = TextChunker.chunk(extracted.pages(), CHUNK_MAX_CHARS, CHUNK_OVERLAP_CHARS);
        if (chunks.isEmpty()) {
            throw new PermanentIngestionException("No text could be extracted from this PDF (it may be a scan).");
        }
        List<float[]> vectors = embeddings.embed(chunks.stream().map(TextChunker.Chunk::content).toList());

        List<ChunkStore.NewChunk> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TextChunker.Chunk chunk = chunks.get(i);
            rows.add(new ChunkStore.NewChunk(chunk.index(), chunk.pageFrom(), chunk.pageTo(), null,
                    chunk.content(), chunk.tokenCount(), vectors.get(i)));
        }

        tx.executeWithoutResult(status -> {
            Document document = documents.findById(documentId).orElse(null);
            if (document == null || document.isDeleted()) {
                return;   // deleted while we were working: do not store its passages
            }
            chunkStore.replaceAll(document.getOrgId(), document.getProjectId(), document.getId(),
                    embeddings.modelName(), rows);
            document.markReady(pageCount, clock.instant());
        });
    }

    void markFailed(UUID documentId, String userMessage) {
        tx.executeWithoutResult(status -> documents.findById(documentId).ifPresent(document -> {
            if (!document.isDeleted()) {
                document.markFailed(userMessage, clock.instant());
            }
        }));
    }
}
