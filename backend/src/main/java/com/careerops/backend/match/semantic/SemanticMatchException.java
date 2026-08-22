package com.careerops.backend.match.semantic;

public class SemanticMatchException extends RuntimeException {
    public enum Reason {
        NETWORK_TIMEOUT, PROVIDER_4XX, PROVIDER_RETRY_EXHAUSTED, MALFORMED_RESPONSE,
        UNKNOWN_PKB_ID, SCORE_OUT_OF_RANGE
    }

    private final Reason reason;

    public SemanticMatchException(Reason reason) {
        super("semantic match failed");
        this.reason = reason;
    }

    public SemanticMatchException(Reason reason, Throwable cause) {
        super("semantic match failed", cause);
        this.reason = reason;
    }

    public Reason reason() { return reason; }

    public boolean isValidationFailure() {
        return reason == Reason.UNKNOWN_PKB_ID || reason == Reason.SCORE_OUT_OF_RANGE;
    }
}
