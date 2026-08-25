package com.careerops.backend.notification.kakao;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "kakao_oauth_token")
public class KakaoOauthToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 500) private String refreshToken;
    private Instant refreshTokenExpiresAt;
    @Column(nullable = false) private Instant updatedAt;
    protected KakaoOauthToken() {}
    KakaoOauthToken(String refreshToken) { this.refreshToken = refreshToken; this.updatedAt = Instant.now(); }
    void rotate(String value, Instant expiresAt) { refreshToken = value; refreshTokenExpiresAt = expiresAt; updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getRefreshToken() { return refreshToken; }
    public Instant getRefreshTokenExpiresAt() { return refreshTokenExpiresAt; }
}
