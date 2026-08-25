package com.careerops.backend.notification;

public class KakaoDeliveryException extends RuntimeException {
    public KakaoDeliveryException(Throwable cause) { super(cause); }
    public KakaoDeliveryException(String message) { super(message); }
}
