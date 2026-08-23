package com.careerops.backend.applicationdraft.llm;

public class ApplicationDraftException extends RuntimeException {
    public enum Reason { NETWORK_TIMEOUT, PROVIDER_4XX, PROVIDER_RETRY_EXHAUSTED,
        MALFORMED_RESPONSE, UNKNOWN_CANDIDATE_ID, UNKNOWN_QUESTION_ID,
        MISSING_QUESTION_RESULT, DUPLICATE_QUESTION_RESULT, AGENT_ANALYSIS_FAILED }
    private final Reason reason;
    public ApplicationDraftException(Reason reason) { super("application draft failed"); this.reason=reason; }
    public ApplicationDraftException(Reason reason,Throwable cause) { super("application draft failed",cause); this.reason=reason; }
    public Reason reason(){ return reason; }
    public boolean isValidationFailure(){ return reason==Reason.UNKNOWN_CANDIDATE_ID||reason==Reason.UNKNOWN_QUESTION_ID
            ||reason==Reason.MISSING_QUESTION_RESULT||reason==Reason.DUPLICATE_QUESTION_RESULT; }
}
