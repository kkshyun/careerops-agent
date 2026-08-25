package com.careerops.backend.notification.kakao;

public interface KakaoTokenClient {
    KakaoTokenRefreshResult refresh(String refreshToken);
}
