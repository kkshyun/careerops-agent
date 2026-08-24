package com.careerops.backend.recommend;

import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.recommend.dto.RawRecommendationResult;
import java.util.List;
import java.util.Map;

public interface JobRecommendationClient {
    RawRecommendationResult recommend(List<JobPosting> jobs, List<CareerExperience> experiences,
            Map<Long,List<ExperienceTag>> tags, List<Certification> certifications,
            List<Education> educations, List<Award> awards, int limit);
}
