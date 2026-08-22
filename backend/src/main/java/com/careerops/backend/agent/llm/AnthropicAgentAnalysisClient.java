package com.careerops.backend.agent.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import com.anthropic.errors.*;
import com.anthropic.models.messages.*;
import com.careerops.backend.agent.llm.dto.RawAgentAnalysisResult;
import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.match.dto.SemanticMatchEvidence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.*;

@Component
public class AnthropicAgentAnalysisClient implements AgentAnalysisClient {
    static final long MAX_TOKENS=8_192;
    private final AgentAnalysisPromptBuilder promptBuilder; private final String apiKey,model;
    private final Duration connectTimeout,requestTimeout;
    public AnthropicAgentAnalysisClient(AgentAnalysisPromptBuilder promptBuilder,
            @Value("${careerops.ai.api-key}") String apiKey,@Value("${careerops.ai.model}") String model,
            @Value("${careerops.ai.agent.connect-timeout-seconds}") long connect,
            @Value("${careerops.ai.agent.request-timeout-seconds}") long request){
        this.promptBuilder=promptBuilder; this.apiKey=apiKey; this.model=model;
        this.connectTimeout=Duration.ofSeconds(connect); this.requestTimeout=Duration.ofSeconds(request); }
    @Override public RawAgentAnalysisResult analyze(JobPosting p,List<CareerExperience> e,Map<Long,List<ExperienceTag>> t,
            Map<Long,List<ExperienceBullet>> b,List<SemanticMatchEvidence> em,List<Certification> c,List<SemanticMatchEvidence> cm,
            List<Education> d,List<SemanticMatchEvidence> dm,List<Award> a,List<SemanticMatchEvidence> am,List<String> gaps){
        if(apiKey==null||apiKey.isBlank()) throw new AgentAnalysisException(AgentAnalysisException.Reason.PROVIDER_4XX);
        try { AnthropicClient client=AnthropicOkHttpClient.builder().apiKey(apiKey)
                    .timeout(Timeout.builder().connect(connectTimeout).request(requestTimeout).build()).build();
            StructuredMessageCreateParams<RawAgentAnalysisResult> params=MessageCreateParams.builder().model(model).maxTokens(MAX_TOKENS)
                    .system(promptBuilder.systemPrompt()).addUserMessage(promptBuilder.userPrompt(p,e,t,b,em,c,cm,d,dm,a,am,gaps))
                    .outputConfig(RawAgentAnalysisResult.class).build();
            return client.messages().create(params).content().stream().flatMap(block->block.text().stream()).findFirst()
                    .orElseThrow(()->new AgentAnalysisException(AgentAnalysisException.Reason.MALFORMED_RESPONSE)).text();
        } catch(AgentAnalysisException ex){ throw ex; } catch(RuntimeException ex){ throw classify(ex); }
    }
    static AgentAnalysisException classify(RuntimeException ex){
        if(ex instanceof AnthropicIoException||hasCause(ex,SocketTimeoutException.class)||hasCause(ex,IOException.class)||ex.getClass().getSimpleName().contains("Timeout"))
            return new AgentAnalysisException(AgentAnalysisException.Reason.NETWORK_TIMEOUT,ex);
        if(ex instanceof AnthropicServiceException service){ if(service.statusCode()==429||service.statusCode()>=500) return new AgentAnalysisException(AgentAnalysisException.Reason.PROVIDER_RETRY_EXHAUSTED,ex); if(service.statusCode()>=400) return new AgentAnalysisException(AgentAnalysisException.Reason.PROVIDER_4XX,ex); }
        return new AgentAnalysisException(AgentAnalysisException.Reason.MALFORMED_RESPONSE,ex);
    }
    private static boolean hasCause(Throwable value,Class<? extends Throwable> type){ for(Throwable c=value;c!=null;c=c.getCause()) if(type.isInstance(c)) return true; return false; }
}
