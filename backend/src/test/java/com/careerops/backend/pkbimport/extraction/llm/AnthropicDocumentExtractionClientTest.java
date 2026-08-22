package com.careerops.backend.pkbimport.extraction.llm;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.StopReason;
import com.careerops.backend.pkbimport.DocumentType;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnthropicDocumentExtractionClientTest {
    @Test
    void usesExpandedOutputTokenBudget() {
        assertThat(AnthropicDocumentExtractionClient.MAX_TOKENS).isEqualTo(16_000);
    }

    @Test
    void distinguishesTokenTruncationWithoutLoggingResponseOrInput() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            assertThatThrownBy(() -> AnthropicDocumentExtractionClient.rejectTruncatedResponse(StopReason.MAX_TOKENS))
                    .isInstanceOfSatisfying(LlmExtractionException.class,
                            exception -> assertThat(exception.reason())
                                    .isEqualTo(LlmExtractionException.Reason.MALFORMED_RESPONSE))
                    .hasCauseInstanceOf(AnthropicDocumentExtractionClient.OutputTruncatedException.class)
                    .cause().hasMessage("max_tokens");
            assertThat(appender.list).isEmpty();
        } finally {
            root.detachAppender(appender);
        }
    }

    @Test
    void classifiesTimeoutProviderErrorsAndMalformedResponses() {
        assertReason(new RuntimeException(new SocketTimeoutException("secret timeout response")),
                LlmExtractionException.Reason.NETWORK_TIMEOUT);
        assertReason(serviceException(401), LlmExtractionException.Reason.PROVIDER_4XX);
        assertReason(serviceException(429), LlmExtractionException.Reason.PROVIDER_RETRY_EXHAUSTED);
        assertReason(serviceException(503), LlmExtractionException.Reason.PROVIDER_RETRY_EXHAUSTED);
        assertReason(new FakeInvalidDataException(), LlmExtractionException.Reason.MALFORMED_RESPONSE);
    }

    @Test
    void missingKeyAndSensitiveInputAreNotLogged() {
        String rawText = "RAW-TEXT-MUST-NOT-APPEAR";
        String apiKey = "API-KEY-MUST-NOT-APPEAR";
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            AnthropicDocumentExtractionClient client = new AnthropicDocumentExtractionClient(
                    new ExtractionPromptBuilder(), "", "model", 10, 60);
            assertThatThrownBy(() -> client.extract(rawText, DocumentType.RESUME))
                    .isInstanceOf(LlmExtractionException.class);
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + right);
            assertThat(logs).doesNotContain(rawText, apiKey);
        } finally {
            root.detachAppender(appender);
        }
    }

    private void assertReason(RuntimeException source, LlmExtractionException.Reason reason) {
        assertThat(AnthropicDocumentExtractionClient.classify(source).reason()).isEqualTo(reason);
    }

    private AnthropicServiceException serviceException(int statusCode) {
        AnthropicServiceException exception = mock(AnthropicServiceException.class);
        when(exception.statusCode()).thenReturn(statusCode);
        return exception;
    }

    static class FakeInvalidDataException extends RuntimeException {}
}
