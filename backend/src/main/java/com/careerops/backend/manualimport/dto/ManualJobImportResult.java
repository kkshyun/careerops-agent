package com.careerops.backend.manualimport.dto;

import com.careerops.backend.job.dto.JobPostingResponse;

public record ManualJobImportResult(
        String result,
        JobPostingResponse job
) {
}
