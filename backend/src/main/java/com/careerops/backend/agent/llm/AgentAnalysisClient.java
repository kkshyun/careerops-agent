package com.careerops.backend.agent.llm;

import com.careerops.backend.agent.llm.dto.RawAgentAnalysisResult;
import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.match.dto.SemanticMatchEvidence;
import java.util.List;
import java.util.Map;

public interface AgentAnalysisClient {
    RawAgentAnalysisResult analyze(JobPosting posting,
            List<CareerExperience> experiences, Map<Long, List<ExperienceTag>> tagsByExperience,
            Map<Long, List<ExperienceBullet>> bulletsByExperience,
            List<SemanticMatchEvidence> experienceMatchContext,
            List<Certification> certifications, List<SemanticMatchEvidence> certificationMatchContext,
            List<Education> educations, List<SemanticMatchEvidence> educationMatchContext,
            List<Award> awards, List<SemanticMatchEvidence> awardMatchContext, List<String> matchGaps);
}
