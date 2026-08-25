package com.careerops.backend.notification.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;
import java.util.Map;

@Component
public class RestClientKakaoMessageClient implements KakaoMessageClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    public RestClientKakaoMessageClient(RestClient.Builder builder, ObjectMapper mapper,
            @Value("${careerops.kakao.api-base-url}") String baseUrl,
            @Value("${careerops.kakao.connect-timeout-seconds}") long connectTimeout,
            @Value("${careerops.kakao.request-timeout-seconds}") long requestTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        requestFactory.setReadTimeout(Duration.ofSeconds(requestTimeout));
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build(); this.mapper = mapper;
    }
    @Override public void sendToMe(String accessToken, String text, String linkUrl) {
        try {
            String template = mapper.writeValueAsString(Map.of("object_type", "text", "text", text,
                    "link", Map.of("web_url", linkUrl)));
            var form = new LinkedMultiValueMap<String, String>(); form.add("template_object", template);
            MessageResponse response = client.post().uri("/v2/api/talk/memo/default/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, value) -> {
                        throw new KakaoApiException(KakaoApiException.Reason.PROVIDER_ERROR,
                                "Kakao message request failed with HTTP " + value.getStatusCode().value());
                    }).onStatus(HttpStatusCode::is5xxServerError, (request, value) -> {
                        throw new KakaoApiException(KakaoApiException.Reason.PROVIDER_5XX,
                                "Kakao message request failed with HTTP " + value.getStatusCode().value());
                    }).body(MessageResponse.class);
            if (response == null || response.resultCode() != 0) {
                throw new KakaoApiException(KakaoApiException.Reason.PROVIDER_ERROR, "Kakao rejected message request");
            }
        } catch (KakaoApiException exception) { throw exception;
        } catch (JacksonException exception) {
            throw new KakaoApiException(KakaoApiException.Reason.PROVIDER_ERROR, "Could not encode Kakao template", exception);
        } catch (ResourceAccessException exception) {
            throw new KakaoApiException(KakaoApiException.Reason.DELIVERY_UNKNOWN, "Kakao message delivery is unknown", exception);
        } catch (RestClientException exception) {
            throw new KakaoApiException(KakaoApiException.Reason.PROVIDER_ERROR, "Kakao message request failed", exception);
        }
    }
    private record MessageResponse(@JsonProperty("result_code") int resultCode) {}
}
