package com.careerops.backend.agent;

import com.careerops.backend.agent.llm.AgentAnalysisPromptBuilder;
import com.careerops.backend.agent.llm.dto.*;
import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.match.dto.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class AgentAnalysisContractTest {
    @Test void structuredOutputHasNoForbiddenFields(){
        for(Class<?> type:List.of(RawAgentAnalysisResult.class,RawExperienceRecommendation.class,RawPkbRecommendation.class))
            assertThat(Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
                    .doesNotContain("score","relevanceScore","priority","gaps","type");
    }
    @Test void promptIncludesOriginalContextAndBulletsButNeverSemanticReason(){
        JobPosting job=new JobPosting("company","job","regular","new","degree","OPEN","i","category","place",LocalDate.now(),LocalDate.now(),"MANUAL","https://example.com","external");
        CareerExperience exp=new CareerExperience(ExperienceType.PROJECT,"title","org","role",null,null,"summary","detail");
        SemanticMatchEvidence match=new SemanticMatchEvidence("CAREER_EXPERIENCE",null,"title",.42,List.of(EvidenceSource.EXPERIENCE_DETAIL),"UNIQUE_REASON_MARKER_9f1a");
        String prompt=new AgentAnalysisPromptBuilder().userPrompt(job,List.of(exp),new HashMap<>(),new HashMap<>(),List.of(match),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of("gap"));
        assertThat(prompt).contains("summary","detail","semanticMatchScore=0.42","matchEvidence=[EXPERIENCE_DETAIL]","gap").doesNotContain("UNIQUE_REASON_MARKER_9f1a");
    }
}
