package com.careerops.backend.notification.kakao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class KakaoTokenStore {
    private final KakaoOauthTokenRepository repository; private final String initialRefreshToken;
    public KakaoTokenStore(KakaoOauthTokenRepository repository,
            @Value("${careerops.kakao.initial-refresh-token}") String initialRefreshToken) {
        this.repository = repository; this.initialRefreshToken = initialRefreshToken;
    }
    @Transactional
    public synchronized String currentRefreshToken() {
        return repository.findFirstByOrderByIdAsc().map(KakaoOauthToken::getRefreshToken)
                .orElseGet(() -> {
                    if (initialRefreshToken == null || initialRefreshToken.isBlank())
                        throw new KakaoApiException(KakaoApiException.Reason.TOKEN_REFRESH_FAILED,
                                "Kakao refresh token is not configured");
                    return repository.save(new KakaoOauthToken(initialRefreshToken)).getRefreshToken();
                });
    }
    @Transactional
    public void rotateIfPresent(String newToken, Instant expiresAt) {
        if (newToken == null || newToken.isBlank()) return;
        KakaoOauthToken token = repository.findFirstByOrderByIdAsc().orElseThrow();
        token.rotate(newToken, expiresAt);
    }
}
