package com.careerops.backend.career.dto;

import com.careerops.backend.career.Award;
import org.springframework.data.domain.Page;
import java.util.List;

public record AwardListResponse(List<AwardResponse> content, long totalElements,
                                int totalPages, int page, int size) {
    public static AwardListResponse from(Page<Award> page) {
        return new AwardListResponse(page.getContent().stream().map(AwardResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
