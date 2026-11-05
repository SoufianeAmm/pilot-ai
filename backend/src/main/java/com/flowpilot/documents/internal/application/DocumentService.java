package com.flowpilot.documents.internal.application;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.documents.internal.DocumentProperties;
import com.flowpilot.documents.internal.domain.Document;
import com.flowpilot.documents.internal.persistence.DocumentRepository;
import com.flowpilot.documents.internal.storage.DocumentStorage;
import com.flowpilot.documents.internal.storage.FileTooLargeException;
import com.flowpilot.documents.internal.storage.StoredFile;
import com.flowpilot.shared.id.Ids;
import com.flowpilot.shared.web.ApiException;
import com.flowpilot.shared.web.ProblemTypes;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DocumentService {

    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final int MAX_FILENAME_LENGTH = 255;

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final DocumentProperties properties;
    private final Clock clock;

    DocumentService(DocumentRepository documents, DocumentStorage storage,
                    DocumentProperties properties, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Document upload(UUID orgId, UUID projectId, UUID uploadedBy,
                           String originalFilename, InputStream content) {
        String filename = cleanFilename(originalFilename);
        UUID id = Ids.newId();
        // The key is built from ids only, never from the user-supplied filename.
        String storageKey = orgId + "/" + projectId + "/" + id;

        StoredFile stored = store(storageKey, content);

        try {
            if (documents.existsByProjectIdAndSha256AndDeletedAtIsNull(projectId, stored.sha256())) {
                deleteQuietly(storageKey);
                throw ApiException.conflict("This document has already been uploaded to the project.");
            }
            Document document = new Document(id, orgId, projectId, filename, PDF_CONTENT_TYPE,
                    stored.sizeBytes(), stored.sha256(), storageKey, uploadedBy, clock.instant());
            return documents.saveAndFlush(document);
        } catch (DataIntegrityViolationException e) {
            // two identical uploads raced: the unique index on (project_id, sha256) caught the second
            deleteQuietly(storageKey);
            throw ApiException.conflict("This document has already been uploaded to the project.");
        } catch (RuntimeException e) {
            deleteQuietly(storageKey);   // never leave an orphan file when the database write fails
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<Document> list(UUID projectId, Pageable pageable) {
        return documents.findByProjectIdAndDeletedAtIsNull(projectId, pageable);
    }

    @Transactional(readOnly = true)
    public Document get(UUID projectId, UUID documentId) {
        return documents.findByIdAndProjectIdAndDeletedAtIsNull(documentId, projectId)
                .orElseThrow(() -> ApiException.notFound("Document not found."));
    }

    public InputStream openContent(Document document) {
        try {
            return storage.open(document.getStorageKey());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public Document delete(UUID projectId, UUID documentId) {
        Document document = documents.findByIdAndProjectIdAndDeletedAtIsNull(documentId, projectId)
                .orElseThrow(() -> ApiException.notFound("Document not found."));
        document.softDelete(clock.instant());
        String storageKey = document.getStorageKey();
        // remove the file only once the database change is committed
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(storageKey);
            }
        });
        return document;
    }


    private StoredFile store(String storageKey, InputStream content) {
        try {
            BufferedInputStream in = new BufferedInputStream(content);
            requirePdfSignature(in);
            return storage.store(storageKey, in, properties.maxFileSize().toBytes());
        } catch (FileTooLargeException e) {
            throw new ApiException(HttpStatus.valueOf(413), ProblemTypes.VALIDATION, "File too large",
                    "The file exceeds the maximum size of " + properties.maxFileSize().toMegabytes() + " MB.");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A real PDF starts with "%PDF-". The extension and the Content-Type header are client-controlled. */
    private static void requirePdfSignature(BufferedInputStream in) throws IOException {
        in.mark(PDF_SIGNATURE.length);
        byte[] head = in.readNBytes(PDF_SIGNATURE.length);
        in.reset();
        if (!Arrays.equals(head, PDF_SIGNATURE)) {
            throw invalidFile("Only PDF files are accepted.");
        }
    }

    private static String cleanFilename(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "")
                .strip();
        if (name.isEmpty()) {
            throw invalidFile("The file name is missing.");
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            throw invalidFile("The file name is too long.");
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw invalidFile("Only PDF files are accepted.");
        }
        return name;
    }

    private static ApiException invalidFile(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION, "Invalid file", detail);
    }

    private void deleteQuietly(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (IOException ignored) {
            // best effort: an orphan file is harmless compared with hiding the original error
        }
    }
}