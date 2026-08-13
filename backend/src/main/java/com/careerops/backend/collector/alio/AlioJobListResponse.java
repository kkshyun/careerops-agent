package com.careerops.backend.collector.alio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlioJobListResponse(
        List<AlioJobItem> result,
        String resultCode,
        String resultMsg,
        int totalCount
) {
}
