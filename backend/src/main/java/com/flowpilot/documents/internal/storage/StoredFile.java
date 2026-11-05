package com.flowpilot.documents.internal.storage;

/** Result of storing a file: where it is, how big it is, and its SHA-256. */
public record StoredFile(String storageKey, long sizeBytes, byte[] sha256) {
}