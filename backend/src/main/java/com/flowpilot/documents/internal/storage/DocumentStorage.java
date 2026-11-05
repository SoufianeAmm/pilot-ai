package com.flowpilot.documents.internal.storage;

import java.io.IOException;
import java.io.InputStream;

public interface DocumentStorage {

    /** Stores the stream under the key, computing size and SHA-256 on the way. Rejects files over maxBytes. */
    StoredFile store(String storageKey, InputStream content, long maxBytes) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}