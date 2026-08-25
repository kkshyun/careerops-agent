package com.careerops.backend.recommend;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.lang.reflect.Method;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class RecommendationTransactionBoundaryTest {
    @Test void onlyReaderReadDeclaresReadOnlyTransaction() throws Exception {
        Transactional annotation=RecommendationCandidateReader.class.getMethod("read").getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull(); assertThat(annotation.readOnly()).isTrue();
        assertThat(JobRecommendationService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(JobRecommendationService.class.getDeclaredMethods()).allMatch(method->method.getAnnotation(Transactional.class)==null);
    }
    @Test void clientInterfaceUsesImmutableSnapshotSignature() throws Exception {
        Method method=JobRecommendationClient.class.getMethod("recommend",com.careerops.backend.recommend.dto.RecommendationInput.class,int.class);
        assertThat(method.getParameterTypes()).containsExactly(com.careerops.backend.recommend.dto.RecommendationInput.class,int.class);
    }
    @Test void repairabilityDiffersFromValidationMetricClassification(){
        for(var reason:JobRecommendationException.Reason.values()){
            JobRecommendationException e=new JobRecommendationException(reason);
            assertThat(e.isRepairable()).isEqualTo(Set.of(JobRecommendationException.Reason.UNKNOWN_JOB_ID,JobRecommendationException.Reason.UNKNOWN_PKB_ID,JobRecommendationException.Reason.SCORE_OUT_OF_RANGE,JobRecommendationException.Reason.MALFORMED_RESPONSE).contains(reason));
            assertThat(e.isValidationFailure()).isEqualTo(Set.of(JobRecommendationException.Reason.UNKNOWN_JOB_ID,JobRecommendationException.Reason.UNKNOWN_PKB_ID,JobRecommendationException.Reason.SCORE_OUT_OF_RANGE).contains(reason));
        }
    }
}
