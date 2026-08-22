package com.careerops.backend.agent.llm;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.anthropic.errors.AnthropicServiceException;
import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnthropicAgentAnalysisClientTest {
    @Test void classifiesProviderFailures(){
        assertThat(AnthropicAgentAnalysisClient.classify(new RuntimeException(new SocketTimeoutException())).reason()).isEqualTo(AgentAnalysisException.Reason.NETWORK_TIMEOUT);
        AnthropicServiceException unauthorized=mock(AnthropicServiceException.class); when(unauthorized.statusCode()).thenReturn(401);
        assertThat(AnthropicAgentAnalysisClient.classify(unauthorized).reason()).isEqualTo(AgentAnalysisException.Reason.PROVIDER_4XX);
        AnthropicServiceException unavailable=mock(AnthropicServiceException.class); when(unavailable.statusCode()).thenReturn(503);
        assertThat(AnthropicAgentAnalysisClient.classify(unavailable).reason()).isEqualTo(AgentAnalysisException.Reason.PROVIDER_RETRY_EXHAUSTED);
        assertThat(AnthropicAgentAnalysisClient.classify(new RuntimeException()).reason()).isEqualTo(AgentAnalysisException.Reason.MALFORMED_RESPONSE);
    }
    @Test void missingKeyAndSensitiveInputAreNotLogged(){
        String sensitive="SENSITIVE-AGENT-PKB-CONTENT",key="SECRET-AGENT-KEY";
        JobPosting job=new JobPosting("company","title","regular","new","degree","OPEN","I","IT","Seoul",LocalDate.now(),LocalDate.now(),"MANUAL","url","id");
        CareerExperience exp=new CareerExperience(ExperienceType.PROJECT,"exp",null,null,null,null,sensitive,sensitive);
        Logger root=(Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME); ListAppender<ILoggingEvent> appender=new ListAppender<>(); appender.start(); root.addAppender(appender);
        try { AnthropicAgentAnalysisClient client=new AnthropicAgentAnalysisClient(new AgentAnalysisPromptBuilder(),"","model",10,60);
            assertThatThrownBy(()->client.analyze(job,List.of(exp),Map.of(),Map.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of()))
                    .isInstanceOfSatisfying(AgentAnalysisException.class,e->assertThat(e.reason()).isEqualTo(AgentAnalysisException.Reason.PROVIDER_4XX));
            String logs=appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("",String::concat); assertThat(logs).doesNotContain(sensitive,key);
        } finally { root.detachAppender(appender); }
    }
}
