package com.careerops.backend.notification.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;
import java.time.Instant;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
public class RestClientKakaoTokenClient implements KakaoTokenClient {
    private final RestClient client; private final String restApiKey; private final String clientSecret;
    public RestClientKakaoTokenClient(RestClient.Builder builder,
            @Value("${careerops.kakao.auth-base-url}") String baseUrl,
            @Value("${careerops.kakao.rest-api-key}") String restApiKey,
            @Value("${careerops.kakao.client-secret}") String clientSecret,
            @Value("${careerops.kakao.connect-timeout-seconds}") long connectTimeout,
            @Value("${careerops.kakao.request-timeout-seconds}") long requestTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        requestFactory.setReadTimeout(Duration.ofSeconds(requestTimeout));
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build(); this.restApiKey = restApiKey; this.clientSecret = clientSecret;
    }
    @Override public KakaoTokenRefreshResult refresh(String refreshToken) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "refresh_token"); form.add("client_id", restApiKey);
            form.add("client_secret", clientSecret); form.add("refresh_token", refreshToken);
            TokenResponse response = client.post().uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank())
                throw new KakaoApiException(KakaoApiException.Reason.TOKEN_REFRESH_FAILED, "Kakao token response was invalid");
            Instant expiresAt = response.refreshTokenExpiresIn() == null ? null : Instant.now().plusSeconds(response.refreshTokenExpiresIn());
            return new KakaoTokenRefreshResult(response.accessToken(), response.refreshToken(), expiresAt);
        } catch (KakaoApiException exception) { throw exception;
        } catch (RestClientException exception) {
            throw new KakaoApiException(KakaoApiException.Reason.TOKEN_REFRESH_FAILED, "Kakao token refresh failed", exception);
        }
    }
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("refresh_token_expires_in") Long refreshTokenExpiresIn) {}
}
