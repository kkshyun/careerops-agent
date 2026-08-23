package com.careerops.backend.applicationdraft.llm;

import com.careerops.backend.agent.dto.AgentAnalysisResponse;
import com.careerops.backend.applicationdraft.dto.QuestionRequest;
import com.careerops.backend.applicationdraft.llm.dto.*;
import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import java.util.*;

public interface ApplicationDraftClient {
    RawApplicationDraftResult plan(JobPosting posting, AgentAnalysisResponse strategy,
            List<CareerExperience> experiences, Map<Long,List<ExperienceTag>> tagsByExperience,
            Map<Long,List<ExperienceBullet>> bulletsByExperience, List<Certification> certifications,
            List<Education> educations, List<Award> awards, List<QuestionRequest> questions);
    RawApplicationDraftRepairResult repair(List<QuestionRequest> overLimitQuestions,
            List<RawQuestionDraft> overLimitDrafts);
}
