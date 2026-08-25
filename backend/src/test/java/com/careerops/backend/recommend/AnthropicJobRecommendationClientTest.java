package com.careerops.backend.recommend;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.anthropic.errors.AnthropicServiceException;
import com.careerops.backend.recommend.dto.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnthropicJobRecommendationClientTest {
    @Test
    void classifiesTimeout4xx5xxAndMalformedFailures() {
        assertThat(AnthropicJobRecommendationClient.classify(
                new RuntimeException(new SocketTimeoutException())).reason())
                .isEqualTo(JobRecommendationException.Reason.NETWORK_TIMEOUT);

        AnthropicServiceException unauthorized=mock(AnthropicServiceException.class);
        when(unauthorized.statusCode()).thenReturn(401);
        assertThat(AnthropicJobRecommendationClient.classify(unauthorized).reason())
                .isEqualTo(JobRecommendationException.Reason.PROVIDER_4XX);

        AnthropicServiceException rateLimited=mock(AnthropicServiceException.class);
        when(rateLimited.statusCode()).thenReturn(429);
        assertThat(AnthropicJobRecommendationClient.classify(rateLimited).reason())
                .isEqualTo(JobRecommendationException.Reason.PROVIDER_RETRY_EXHAUSTED);

        AnthropicServiceException unavailable=mock(AnthropicServiceException.class);
        when(unavailable.statusCode()).thenReturn(503);
        assertThat(AnthropicJobRecommendationClient.classify(unavailable).reason())
                .isEqualTo(JobRecommendationException.Reason.PROVIDER_RETRY_EXHAUSTED);

        assertThat(AnthropicJobRecommendationClient.classify(new FakeJsonException()).reason())
                .isEqualTo(JobRecommendationException.Reason.MALFORMED_RESPONSE);
    }

    @Test
    void missingKeyAndSensitiveInputAreNotLogged() {
        String sensitiveJobTitle="SENSITIVE-RECOMMENDATION-JOB-TITLE";
        String sensitivePkbText="SENSITIVE-RECOMMENDATION-PKB-SUMMARY-AND-DETAIL";
        String key="SECRET-RECOMMENDATION-API-KEY";
        RecommendationInput input=new RecommendationInput(List.of(new RecommendationJobCandidate(1L,"Sensitive Company",sensitiveJobTitle,"IT","new","degree",LocalDate.now())),
                List.of(new RecommendationExperience(2L,"Sensitive Experience",null,null,sensitivePkbText,List.of())),List.of(),List.of(),List.of());
        Logger root=(Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender=new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            AnthropicJobRecommendationClient client=new AnthropicJobRecommendationClient(
                    new JobRecommendationPromptBuilder(),"","model",10,90);

            assertThatThrownBy(() -> client.recommend(input,20))
                    .isInstanceOfSatisfying(JobRecommendationException.class,
                            exception -> assertThat(exception.reason())
                                    .isEqualTo(JobRecommendationException.Reason.PROVIDER_4XX));

            String logs=appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("",String::concat);
            assertThat(logs).doesNotContain(sensitiveJobTitle,sensitivePkbText,key);
        } finally {
            root.detachAppender(appender);
        }
    }

    static class FakeJsonException extends RuntimeException {}
}
