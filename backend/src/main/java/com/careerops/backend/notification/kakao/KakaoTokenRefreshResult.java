package com.careerops.backend.notification.kakao;

import java.time.Instant;

public record KakaoTokenRefreshResult(String accessToken, String newRefreshToken,
        Instant newRefreshTokenExpiresAt) {}
