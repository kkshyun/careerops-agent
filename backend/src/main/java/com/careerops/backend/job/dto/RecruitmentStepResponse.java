package com.careerops.backend.job.dto;

import com.careerops.backend.job.RecruitmentStep;

public record RecruitmentStepResponse(
        Integer sortNo,
        String stepGroupName,
        Double competitionRate,
        Integer applicantCount,
        Integer recruitCount,
        String occurredAtRaw
) {
    public static RecruitmentStepResponse from(RecruitmentStep recruitmentStep) {
        return new RecruitmentStepResponse(
                recruitmentStep.getSortNo(),
                recruitmentStep.getStepGroupName(),
                recruitmentStep.getCompetitionRate(),
                recruitmentStep.getApplicantCount(),
                recruitmentStep.getRecruitCount(),
                recruitmentStep.getOccurredAtRaw()
        );
    }
}
