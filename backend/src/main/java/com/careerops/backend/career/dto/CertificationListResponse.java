package com.careerops.backend.career.dto;

import com.careerops.backend.career.Certification;
import org.springframework.data.domain.Page;
import java.util.List;

public record CertificationListResponse(List<CertificationResponse> content, long totalElements,
                                        int totalPages, int page, int size) {
    public static CertificationListResponse from(Page<Certification> page) {
        return new CertificationListResponse(page.getContent().stream().map(CertificationResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
