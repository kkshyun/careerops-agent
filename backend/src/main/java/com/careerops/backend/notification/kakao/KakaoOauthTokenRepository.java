package com.careerops.backend.notification.kakao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface KakaoOauthTokenRepository extends JpaRepository<KakaoOauthToken, Long> {
    Optional<KakaoOauthToken> findFirstByOrderByIdAsc();
}
