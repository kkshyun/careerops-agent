package com.careerops.backend.pkbimport.extraction.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.careerops.backend.pkbimport.DocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

@Component
public class AnthropicDocumentExtractionClient implements DocumentExtractionClient {
    private static final long MAX_TOKENS = 8_192;

    private final ExtractionPromptBuilder promptBuilder;
    private final String apiKey;
    private final String model;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    public AnthropicDocumentExtractionClient(
            ExtractionPromptBuilder promptBuilder,
            @Value("${careerops.ai.api-key}") String apiKey,
            @Value("${careerops.ai.model}") String model,
            @Value("${careerops.ai.connect-timeout-seconds}") long connectTimeoutSeconds,
            @Value("${careerops.ai.request-timeout-seconds}") long requestTimeoutSeconds) {
        this.promptBuilder = promptBuilder;
        this.apiKey = apiKey;
        this.model = model;
        this.connectTimeout = Duration.ofSeconds(connectTimeoutSeconds);
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
    }

    @Override
    public StructuredExtractionResult extract(String rawText, DocumentType documentType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmExtractionException(LlmExtractionException.Reason.PROVIDER_4XX);
        }
        try {
            Timeout timeout = Timeout.builder()
                    .connect(connectTimeout)
                    .request(requestTimeout)
                    .build();
            AnthropicClient client = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .timeout(timeout)
                    .build();
            StructuredMessageCreateParams<StructuredExtractionResult> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(MAX_TOKENS)
                    .system(promptBuilder.systemPrompt(documentType))
                    .addUserMessage(promptBuilder.userPrompt(rawText, documentType))
                    .outputConfig(StructuredExtractionResult.class)
                    .build();
            StructuredMessage<StructuredExtractionResult> response = client.messages().create(params);
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .orElseThrow(() -> new LlmExtractionException(
                            LlmExtractionException.Reason.MALFORMED_RESPONSE))
                    .text();
        } catch (LlmExtractionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw classify(exception);
        }
    }

    static LlmExtractionException classify(RuntimeException exception) {
        if (exception instanceof AnthropicIoException
                || hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, IOException.class)
                || exception.getClass().getSimpleName().contains("Timeout")) {
            return new LlmExtractionException(LlmExtractionException.Reason.NETWORK_TIMEOUT, exception);
        }
        if (exception instanceof AnthropicServiceException serviceException) {
            int status = serviceException.statusCode();
            if (status == 429 || status >= 500) {
                return new LlmExtractionException(LlmExtractionException.Reason.PROVIDER_RETRY_EXHAUSTED, exception);
            }
            if (status >= 400 && status < 500) {
                return new LlmExtractionException(LlmExtractionException.Reason.PROVIDER_4XX, exception);
            }
        }
        if (exception instanceof AnthropicInvalidDataException
                || exception.getClass().getSimpleName().contains("InvalidData")
                || exception.getClass().getSimpleName().contains("Json")) {
            return new LlmExtractionException(LlmExtractionException.Reason.MALFORMED_RESPONSE, exception);
        }
        return new LlmExtractionException(LlmExtractionException.Reason.MALFORMED_RESPONSE, exception);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }
}
