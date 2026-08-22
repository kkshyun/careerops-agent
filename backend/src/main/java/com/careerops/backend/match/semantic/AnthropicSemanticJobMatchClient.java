package com.careerops.backend.match.semantic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.match.semantic.dto.RawSemanticMatchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class AnthropicSemanticJobMatchClient implements SemanticJobMatchClient {
    static final long MAX_TOKENS = 4_096;
    private final SemanticMatchPromptBuilder promptBuilder;
    private final String apiKey;
    private final String model;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    public AnthropicSemanticJobMatchClient(SemanticMatchPromptBuilder promptBuilder,
            @Value("${careerops.ai.api-key}") String apiKey,
            @Value("${careerops.ai.model}") String model,
            @Value("${careerops.ai.match.connect-timeout-seconds}") long connectTimeoutSeconds,
            @Value("${careerops.ai.match.request-timeout-seconds}") long requestTimeoutSeconds) {
        this.promptBuilder = promptBuilder; this.apiKey = apiKey; this.model = model;
        this.connectTimeout = Duration.ofSeconds(connectTimeoutSeconds);
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
    }

    @Override
    public RawSemanticMatchResult match(JobPosting posting, List<CareerExperience> experiences,
            Map<Long, List<ExperienceTag>> tagsByExperience, List<Certification> certifications,
            List<Education> educations, List<Award> awards) {
        if (apiKey == null || apiKey.isBlank()) throw new SemanticMatchException(SemanticMatchException.Reason.PROVIDER_4XX);
        try {
            AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey)
                    .timeout(Timeout.builder().connect(connectTimeout).request(requestTimeout).build()).build();
            StructuredMessageCreateParams<RawSemanticMatchResult> params = MessageCreateParams.builder()
                    .model(model).maxTokens(MAX_TOKENS).system(promptBuilder.systemPrompt())
                    .addUserMessage(promptBuilder.userPrompt(posting, experiences, tagsByExperience,
                            certifications, educations, awards))
                    .outputConfig(RawSemanticMatchResult.class).build();
            StructuredMessage<RawSemanticMatchResult> response = client.messages().create(params);
            return response.content().stream().flatMap(block -> block.text().stream()).findFirst()
                    .orElseThrow(() -> new SemanticMatchException(SemanticMatchException.Reason.MALFORMED_RESPONSE)).text();
        } catch (SemanticMatchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw classify(exception);
        }
    }

    static SemanticMatchException classify(RuntimeException exception) {
        if (exception instanceof AnthropicIoException || hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, IOException.class) || exception.getClass().getSimpleName().contains("Timeout"))
            return new SemanticMatchException(SemanticMatchException.Reason.NETWORK_TIMEOUT, exception);
        if (exception instanceof AnthropicServiceException service) {
            if (service.statusCode() == 429 || service.statusCode() >= 500)
                return new SemanticMatchException(SemanticMatchException.Reason.PROVIDER_RETRY_EXHAUSTED, exception);
            if (service.statusCode() >= 400)
                return new SemanticMatchException(SemanticMatchException.Reason.PROVIDER_4XX, exception);
        }
        if (exception instanceof AnthropicInvalidDataException
                || exception.getClass().getSimpleName().contains("InvalidData")
                || exception.getClass().getSimpleName().contains("Json"))
            return new SemanticMatchException(SemanticMatchException.Reason.MALFORMED_RESPONSE, exception);
        return new SemanticMatchException(SemanticMatchException.Reason.MALFORMED_RESPONSE, exception);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) if (type.isInstance(current)) return true;
        return false;
    }
}
