package com.careerops.backend.applicationdraft.llm;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.careerops.backend.applicationdraft.dto.QuestionRequest;
import com.careerops.backend.applicationdraft.dto.QuestionIntent;
import com.careerops.backend.applicationdraft.llm.dto.RawQuestionDraft;
import com.careerops.backend.career.CareerExperience;
import com.careerops.backend.career.ExperienceType;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnthropicApplicationDraftClientTest {
    @Test void blankKeyFailsWithoutProviderCall(){var client=new AnthropicApplicationDraftClient(new ApplicationDraftPromptBuilder()," ","model",10,150);assertThatThrownBy(()->client.plan(null,null,List.of(),Map.of(),Map.of(),List.of(),List.of(),List.of(),List.of(new QuestionRequest("q","sensitive",null)))).isInstanceOf(ApplicationDraftException.class).extracting("reason").isEqualTo(ApplicationDraftException.Reason.PROVIDER_4XX);}
    @Test void blankKeyFailsBeforeRepairPromptIsBuilt(){var prompts=mock(ApplicationDraftPromptBuilder.class);var client=new AnthropicApplicationDraftClient(prompts,null,"model",10,150);assertThatThrownBy(()->client.repair(null,null)).isInstanceOf(ApplicationDraftException.class).extracting("reason").isEqualTo(ApplicationDraftException.Reason.PROVIDER_4XX);verifyNoInteractions(prompts);}
    @Test void missingKeyAndSensitiveInputsAreNotLogged(){
        String question="SENSITIVE-DRAFT-QUESTION",draft="SENSITIVE-FULL-DRAFT",summary="SENSITIVE-PKB-SUMMARY",detail="SENSITIVE-PKB-DETAIL";
        CareerExperience experience=new CareerExperience(ExperienceType.PROJECT,"title",null,null,null,null,summary,detail);
        RawQuestionDraft raw=new RawQuestionDraft("q",QuestionIntent.OTHER,List.of(),null,List.of(),List.of(),List.of(),List.of(),"core",List.of(),draft,List.of(),false);
        Logger root=(Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);ListAppender<ILoggingEvent> appender=new ListAppender<>();appender.start();root.addAppender(appender);
        try{var client=new AnthropicApplicationDraftClient(new ApplicationDraftPromptBuilder(),"","model",10,150);
            assertThatThrownBy(()->client.plan(null,null,List.of(experience),Map.of(),Map.of(),List.of(),List.of(),List.of(),List.of(new QuestionRequest("q",question,null)))).isInstanceOf(ApplicationDraftException.class);
            assertThatThrownBy(()->client.repair(List.of(new QuestionRequest("q",question,10)),List.of(raw))).isInstanceOf(ApplicationDraftException.class);
            String logs=appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("",String::concat);assertThat(logs).doesNotContain(question,draft,summary,detail);
        }finally{root.detachAppender(appender);}
    }
}
