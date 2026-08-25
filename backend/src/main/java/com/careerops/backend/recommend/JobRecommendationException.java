package com.careerops.backend.recommend;

public class JobRecommendationException extends RuntimeException {
    public enum Reason { NETWORK_TIMEOUT, PROVIDER_4XX, PROVIDER_RETRY_EXHAUSTED, MALFORMED_RESPONSE,
        UNKNOWN_JOB_ID, UNKNOWN_PKB_ID, SCORE_OUT_OF_RANGE }
    private final Reason reason;
    public JobRecommendationException(Reason reason) { super("job recommendation failed"); this.reason=reason; }
    public JobRecommendationException(Reason reason, Throwable cause) { super("job recommendation failed",cause); this.reason=reason; }
    public Reason reason(){ return reason; }
    /** Returns the metric bucket classification; unlike repair eligibility, malformed responses remain provider errors. */
    public boolean isValidationFailure(){ return reason==Reason.UNKNOWN_JOB_ID||reason==Reason.UNKNOWN_PKB_ID||reason==Reason.SCORE_OUT_OF_RANGE; }
    /** Returns whether one application-level repair attempt is allowed; this purpose differs from metric classification. */
    public boolean isRepairable(){ return isValidationFailure()||reason==Reason.MALFORMED_RESPONSE; }
}
