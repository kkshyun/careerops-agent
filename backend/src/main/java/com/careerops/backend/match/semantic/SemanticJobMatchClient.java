package com.careerops.backend.match.semantic;

import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.match.semantic.dto.RawSemanticMatchResult;
import java.util.List;
import java.util.Map;

public interface SemanticJobMatchClient {
    RawSemanticMatchResult match(JobPosting posting, List<CareerExperience> experiences,
                                 Map<Long, List<ExperienceTag>> tagsByExperience,
                                 List<Certification> certifications, List<Education> educations,
                                 List<Award> awards);
}
