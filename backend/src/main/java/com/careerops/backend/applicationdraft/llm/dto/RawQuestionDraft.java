package com.careerops.backend.applicationdraft.llm.dto;

import com.careerops.backend.applicationdraft.dto.QuestionIntent;
import java.util.List;

public record RawQuestionDraft(String questionId, QuestionIntent primaryIntent,
        List<QuestionIntent> secondaryIntents, Long primaryExperienceId,
        List<Long> supportingExperienceIds, List<Long> certificationIds,
        List<Long> educationIds, List<Long> awardIds, String coreMessage,
        List<String> outline, String draft, List<String> warnings,
        boolean missingCompanyContext) {}
