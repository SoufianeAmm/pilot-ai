package com.flowpilot.documents.internal.ingestion;

/** A problem with the document itself: retrying cannot help, so the job fails immediately. */
class PermanentIngestionException extends RuntimeException {

    PermanentIngestionException(String message) {
        super(message);
    }

    PermanentIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
