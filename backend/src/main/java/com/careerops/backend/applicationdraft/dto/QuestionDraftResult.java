package com.careerops.backend.applicationdraft.dto;

import java.util.List;

/**
 * characterCount는 CareerOps 자체 기준인 {@link String#length()} 값이며, 공백 포함 여부나
 * 바이트 기준을 사용하는 특정 채용 사이트의 계산 방식과 다를 수 있다.
 */
public record QuestionDraftResult(String questionId, QuestionIntent primaryIntent,
        List<QuestionIntent> secondaryIntents, Long primaryExperienceId,
        List<Long> supportingExperienceIds, List<Long> certificationIds,
        List<Long> educationIds, List<Long> awardIds, String coreMessage,
        List<String> outline, String draft, int characterCount, Integer maxLength,
        boolean limitExceeded, boolean missingCompanyContext, List<String> warnings) {}
