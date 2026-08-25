package com.careerops.backend.recommend;

import com.careerops.backend.recommend.dto.RecommendationInput;
import com.careerops.backend.recommend.dto.RawRecommendationResult;

public interface JobRecommendationClient {
    RawRecommendationResult recommend(RecommendationInput input, int providerTopK);
}
