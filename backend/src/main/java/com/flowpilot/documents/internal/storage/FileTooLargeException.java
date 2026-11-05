package com.flowpilot.documents.internal.storage;

public class FileTooLargeException extends RuntimeException {

    private final long maxBytes;

    public FileTooLargeException(long maxBytes) {
        super("File exceeds the maximum size of " + maxBytes + " bytes.");
        this.maxBytes = maxBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }
}