package com.careerops.backend.recommend;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.recommend.dto.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobRecommendationServiceLogTest {
    @Test void diagnosticLogsCauseTypesWithoutSensitiveMessagesOrInput(){
        for(var reason:List.of(JobRecommendationException.Reason.NETWORK_TIMEOUT,JobRecommendationException.Reason.MALFORMED_RESPONSE,JobRecommendationException.Reason.PROVIDER_RETRY_EXHAUSTED)){
            JobPostingRepository jobs=mock(JobPostingRepository.class);RecommendationCandidateReader reader=mock(RecommendationCandidateReader.class);JobRecommendationClient client=mock(JobRecommendationClient.class);
            RecommendationInput input=new RecommendationInput(List.of(new RecommendationJobCandidate(1L,"company","SENSITIVE-JOB-TITLE","IT","new","degree",LocalDate.now())),List.of(new RecommendationExperience(2L,"experience",null,null,"SENSITIVE-PKB-SUMMARY",List.of())),List.of(),List.of(),List.of());
            when(reader.read()).thenReturn(input);
            JobRecommendationException failure=new JobRecommendationException(reason,new SensitiveCause("SENSITIVE-PROVIDER-BODY-AND-API-KEY"));
            when(client.recommend(input,20)).thenThrow(failure);
            Logger logger=(Logger)LoggerFactory.getLogger(JobRecommendationService.class);ListAppender<ILoggingEvent> appender=new ListAppender<>();appender.start();logger.addAppender(appender);
            try{catchThrowable(()->new JobRecommendationService(jobs,reader,client,new SimpleMeterRegistry()).recommend(5));String logs=appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("",String::concat);assertThat(logs).contains("causeType=SensitiveCause").doesNotContain("SENSITIVE-JOB-TITLE","SENSITIVE-PKB-SUMMARY","SENSITIVE-PROVIDER-BODY-AND-API-KEY");}finally{logger.detachAppender(appender);}
        }
    }
    static class SensitiveCause extends RuntimeException{SensitiveCause(String message){super(message);}}
}
