package com.careerops.backend.recommend;

import com.anthropic.client.*;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import com.anthropic.errors.*;
import com.anthropic.models.messages.*;
import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.recommend.dto.RawRecommendationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.*;

@Component
public class AnthropicJobRecommendationClient implements JobRecommendationClient {
    static final long MAX_TOKENS=8_192;
    private final JobRecommendationPromptBuilder prompts; private final String apiKey,model; private final Duration connectTimeout,requestTimeout;
    public AnthropicJobRecommendationClient(JobRecommendationPromptBuilder prompts,@Value("${careerops.ai.api-key}") String apiKey,
            @Value("${careerops.ai.model}") String model,@Value("${careerops.ai.recommendation.connect-timeout-seconds}") long connect,
            @Value("${careerops.ai.recommendation.request-timeout-seconds}") long request){ this.prompts=prompts;this.apiKey=apiKey;this.model=model;connectTimeout=Duration.ofSeconds(connect);requestTimeout=Duration.ofSeconds(request); }
    public RawRecommendationResult recommend(List<JobPosting> jobs,List<CareerExperience> exps,Map<Long,List<ExperienceTag>> tags,List<Certification> certs,List<Education> edus,List<Award> awards,int limit){
        if(apiKey==null||apiKey.isBlank()) throw new JobRecommendationException(JobRecommendationException.Reason.PROVIDER_4XX);
        try { AnthropicClient client=AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Timeout.builder().connect(connectTimeout).request(requestTimeout).build()).build();
            StructuredMessageCreateParams<RawRecommendationResult> params=MessageCreateParams.builder().model(model).maxTokens(MAX_TOKENS).system(prompts.systemPrompt()).addUserMessage(prompts.userPrompt(jobs,exps,tags,certs,edus,awards,limit)).outputConfig(RawRecommendationResult.class).build();
            StructuredMessage<RawRecommendationResult> response=client.messages().create(params);
            return response.content().stream().flatMap(v->v.text().stream()).findFirst().orElseThrow(()->new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE)).text();
        } catch(JobRecommendationException e){ throw e; } catch(RuntimeException e){ throw classify(e); } }
    static JobRecommendationException classify(RuntimeException e){
        if(e instanceof AnthropicIoException||hasCause(e,SocketTimeoutException.class)||hasCause(e,IOException.class)||e.getClass().getSimpleName().contains("Timeout")) return new JobRecommendationException(JobRecommendationException.Reason.NETWORK_TIMEOUT,e);
        if(e instanceof AnthropicServiceException s){ if(s.statusCode()==429||s.statusCode()>=500)return new JobRecommendationException(JobRecommendationException.Reason.PROVIDER_RETRY_EXHAUSTED,e); if(s.statusCode()>=400)return new JobRecommendationException(JobRecommendationException.Reason.PROVIDER_4XX,e); }
        return new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE,e); }
    private static boolean hasCause(Throwable t,Class<? extends Throwable> type){ for(Throwable c=t;c!=null;c=c.getCause())if(type.isInstance(c))return true; return false; }
}
