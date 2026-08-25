package com.careerops.backend.notification.kakao;

public class KakaoApiException extends RuntimeException {
    public enum Reason {
        PROVIDER_ERROR("provider_error"), PROVIDER_5XX("provider_5xx"),
        DELIVERY_UNKNOWN("delivery_unknown"), TOKEN_REFRESH_FAILED("token_refresh_failed");
        private final String metricTag;
        Reason(String metricTag) { this.metricTag = metricTag; }
        public String metricTag() { return metricTag; }
    }
    private final Reason reason;
    public KakaoApiException(Reason reason, String message) { super(message); this.reason = reason; }
    public KakaoApiException(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
    public Reason reason() { return reason; }
}
