package com.careerops.backend.applicationdraft.llm.dto;

import java.util.List;

public record RawApplicationDraftRepairResult(List<RawQuestionRepair> results) {
    public record RawQuestionRepair(String questionId, String draft) {}
}
