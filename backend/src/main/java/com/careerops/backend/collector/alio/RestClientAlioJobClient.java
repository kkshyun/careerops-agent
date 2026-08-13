package com.careerops.backend.collector.alio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestClientAlioJobClient implements AlioJobClient {

    private final RestClient restClient;
    private final String apiKey;

    public RestClientAlioJobClient(
            RestClient.Builder restClientBuilder,
            @Value("${careerops.collector.alio.base-url}") String baseUrl,
            @Value("${careerops.collector.alio.api-key}") String apiKey
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public AlioJobListResponse fetchList(int pageNo, int numOfRows) {
        try {
            AlioJobListResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/list.do")
                            .queryParam("serviceKey", apiKey)
                            .queryParam("pageNo", pageNo)
                            .queryParam("numOfRows", numOfRows)
                            .queryParam("resultType", "json")
                            .build())
                    .header("swaggerType", "Y")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .body(AlioJobListResponse.class);
            if (response == null) {
                throw new AlioApiException(AlioApiException.Reason.PARSE_ERROR, "ALIO returned an empty body");
            }
            if (!"200".equals(response.resultCode())) {
                throw new AlioApiException(AlioApiException.Reason.FETCH_ERROR,
                        "ALIO returned resultCode=" + response.resultCode());
            }
            return response;
        } catch (HttpMessageConversionException | DecodingException exception) {
            throw new AlioApiException(AlioApiException.Reason.PARSE_ERROR, "Failed to parse ALIO response", exception);
        } catch (RestClientException exception) {
            throw new AlioApiException(AlioApiException.Reason.FETCH_ERROR, "Failed to fetch ALIO jobs", exception);
        }
    }
}
