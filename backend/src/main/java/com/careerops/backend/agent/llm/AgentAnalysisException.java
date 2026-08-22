package com.careerops.backend.agent.llm;

public class AgentAnalysisException extends RuntimeException {
    public enum Reason {
        NETWORK_TIMEOUT, PROVIDER_4XX, PROVIDER_RETRY_EXHAUSTED, MALFORMED_RESPONSE,
        UNKNOWN_CANDIDATE_ID, SEMANTIC_MATCH_FAILED
    }

    private final Reason reason;

    public AgentAnalysisException(Reason reason) { super("agent analysis failed"); this.reason = reason; }
    public AgentAnalysisException(Reason reason, Throwable cause) { super("agent analysis failed", cause); this.reason = reason; }
    public Reason reason() { return reason; }
    public boolean isValidationFailure() { return reason == Reason.UNKNOWN_CANDIDATE_ID; }
}
