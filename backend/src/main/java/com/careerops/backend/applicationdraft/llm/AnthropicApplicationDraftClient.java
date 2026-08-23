package com.careerops.backend.applicationdraft.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import com.anthropic.errors.*;
import com.anthropic.models.messages.*;
import com.careerops.backend.agent.dto.AgentAnalysisResponse;
import com.careerops.backend.applicationdraft.dto.QuestionRequest;
import com.careerops.backend.applicationdraft.llm.dto.*;
import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.*;

@Component
public class AnthropicApplicationDraftClient implements ApplicationDraftClient {
    static final long MAX_TOKENS=24_000;
    private final ApplicationDraftPromptBuilder prompts; private final String apiKey,model; private final Duration connectTimeout,requestTimeout;
    public AnthropicApplicationDraftClient(ApplicationDraftPromptBuilder prompts,@Value("${careerops.ai.api-key}") String apiKey,
            @Value("${careerops.ai.model}") String model,@Value("${careerops.ai.application-draft.connect-timeout-seconds}") long connect,
            @Value("${careerops.ai.application-draft.request-timeout-seconds}") long request){ this.prompts=prompts;this.apiKey=apiKey;this.model=model;connectTimeout=Duration.ofSeconds(connect);requestTimeout=Duration.ofSeconds(request); }
    @Override public RawApplicationDraftResult plan(JobPosting p,AgentAnalysisResponse s,List<CareerExperience> e,Map<Long,List<ExperienceTag>> t,
            Map<Long,List<ExperienceBullet>> b,List<Certification> c,List<Education> d,List<Award> a,List<QuestionRequest> q){
        requireApiKey();
        return call(RawApplicationDraftResult.class,prompts.systemPrompt(),prompts.userPrompt(p,s,e,t,b,c,d,a,q)); }
    @Override public RawApplicationDraftRepairResult repair(List<QuestionRequest> q,List<RawQuestionDraft> d){
        requireApiKey();
        return call(RawApplicationDraftRepairResult.class,prompts.repairSystemPrompt(),prompts.repairUserPrompt(q,d)); }
    private void requireApiKey(){ if(apiKey==null||apiKey.isBlank()) throw new ApplicationDraftException(ApplicationDraftException.Reason.PROVIDER_4XX); }
    private <T> T call(Class<T> type,String system,String user){ requireApiKey();
        try { AnthropicClient client=AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Timeout.builder().connect(connectTimeout).request(requestTimeout).build()).build();
            StructuredMessageCreateParams<T> params=MessageCreateParams.builder().model(model).maxTokens(MAX_TOKENS).system(system).addUserMessage(user).outputConfig(type).build();
            return client.messages().create(params).content().stream().flatMap(v->v.text().stream()).findFirst().orElseThrow(()->new ApplicationDraftException(ApplicationDraftException.Reason.MALFORMED_RESPONSE)).text();
        }catch(ApplicationDraftException ex){throw ex;}catch(RuntimeException ex){throw classify(ex);} }
    static ApplicationDraftException classify(RuntimeException ex){ if(ex instanceof AnthropicIoException||hasCause(ex,SocketTimeoutException.class)||hasCause(ex,IOException.class)||ex.getClass().getSimpleName().contains("Timeout")) return new ApplicationDraftException(ApplicationDraftException.Reason.NETWORK_TIMEOUT,ex);
        if(ex instanceof AnthropicServiceException s){if(s.statusCode()==429||s.statusCode()>=500)return new ApplicationDraftException(ApplicationDraftException.Reason.PROVIDER_RETRY_EXHAUSTED,ex);if(s.statusCode()>=400)return new ApplicationDraftException(ApplicationDraftException.Reason.PROVIDER_4XX,ex);} return new ApplicationDraftException(ApplicationDraftException.Reason.MALFORMED_RESPONSE,ex); }
    private static boolean hasCause(Throwable v,Class<? extends Throwable> t){for(Throwable c=v;c!=null;c=c.getCause())if(t.isInstance(c))return true;return false;}
}
