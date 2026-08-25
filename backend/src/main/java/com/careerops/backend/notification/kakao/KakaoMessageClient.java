package com.careerops.backend.notification.kakao;

public interface KakaoMessageClient {
    void sendToMe(String accessToken, String text, String linkUrl);
}
