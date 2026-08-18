package com.careerops.backend.pkbimport.extraction.llm;

public class LlmExtractionException extends RuntimeException {
    public enum Reason {
        NETWORK_TIMEOUT,
        PROVIDER_4XX,
        PROVIDER_RETRY_EXHAUSTED,
        MALFORMED_RESPONSE
    }

    private final Reason reason;

    public LlmExtractionException(Reason reason, Throwable cause) {
        super("document extraction failed", cause);
        this.reason = reason;
    }

    public LlmExtractionException(Reason reason) {
        super("document extraction failed");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
