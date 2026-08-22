package com.careerops.backend.match.semantic;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.anthropic.errors.AnthropicServiceException;
import com.careerops.backend.career.CareerExperience;
import com.careerops.backend.career.ExperienceType;
import com.careerops.backend.job.JobPosting;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnthropicSemanticJobMatchClientTest {
    @Test
    void classifiesProviderAndMalformedFailures() {
        assertThat(AnthropicSemanticJobMatchClient.classify(new RuntimeException(new SocketTimeoutException())).reason())
                .isEqualTo(SemanticMatchException.Reason.NETWORK_TIMEOUT);
        AnthropicServiceException unauthorized=mock(AnthropicServiceException.class); when(unauthorized.statusCode()).thenReturn(401);
        assertThat(AnthropicSemanticJobMatchClient.classify(unauthorized).reason()).isEqualTo(SemanticMatchException.Reason.PROVIDER_4XX);
        AnthropicServiceException unavailable=mock(AnthropicServiceException.class); when(unavailable.statusCode()).thenReturn(503);
        assertThat(AnthropicSemanticJobMatchClient.classify(unavailable).reason()).isEqualTo(SemanticMatchException.Reason.PROVIDER_RETRY_EXHAUSTED);
        assertThat(AnthropicSemanticJobMatchClient.classify(new FakeJsonException()).reason()).isEqualTo(SemanticMatchException.Reason.MALFORMED_RESPONSE);
    }

    @Test
    void missingKeyAndSensitiveInputAreNotLogged() {
        String sensitiveJobTitle="SENSITIVE-JOB-TITLE";
        String sensitivePkbText="SENSITIVE-PKB-SUMMARY-AND-DETAIL";
        String key="SECRET-API-KEY";
        JobPosting job=new JobPosting("Sensitive Company",sensitiveJobTitle,"regular","new","degree",
                "OPEN","I", "IT","Seoul",LocalDate.now(),LocalDate.now().plusDays(1),"MANUAL","url","id");
        CareerExperience experience=new CareerExperience(ExperienceType.PROJECT,"Sensitive Experience",null,null,
                null,null,sensitivePkbText,sensitivePkbText);
        Logger root=(Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME); ListAppender<ILoggingEvent> appender=new ListAppender<>();
        appender.start(); root.addAppender(appender);
        try {
            AnthropicSemanticJobMatchClient client=new AnthropicSemanticJobMatchClient(new SemanticMatchPromptBuilder(), "", "model", 10, 45);
            assertThatThrownBy(() -> client.match(job, List.of(experience), java.util.Map.of(), List.of(), List.of(), List.of()))
                    .isInstanceOfSatisfying(SemanticMatchException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(SemanticMatchException.Reason.PROVIDER_4XX));
            String logs=appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
            assertThat(logs).doesNotContain(sensitiveJobTitle, sensitivePkbText, key);
        } finally { root.detachAppender(appender); }
    }
    static class FakeJsonException extends RuntimeException {}
}
