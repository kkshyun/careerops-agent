package com.careerops.backend.applicationdraft.llm.dto;

import java.util.List;

public record RawApplicationDraftResult(RawOverallStrategy overallStrategy, List<RawQuestionDraft> questionResults) {}
