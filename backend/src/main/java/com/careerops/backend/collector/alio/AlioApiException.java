package com.careerops.backend.collector.alio;

public class AlioApiException extends RuntimeException {

    public enum Reason {
        FETCH_ERROR("fetch_error"),
        PARSE_ERROR("parse_error");

        private final String metricTag;

        Reason(String metricTag) {
            this.metricTag = metricTag;
        }

        public String metricTag() {
            return metricTag;
        }
    }

    private final Reason reason;

    public AlioApiException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AlioApiException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
