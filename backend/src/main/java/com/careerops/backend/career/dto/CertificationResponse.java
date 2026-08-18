package com.careerops.backend.career.dto;

import com.careerops.backend.career.Certification;
import java.time.Instant;
import java.time.LocalDate;

public record CertificationResponse(Long id, String name, String issuer, LocalDate acquiredDate,
                                    LocalDate expirationDate, String credentialId, String description,
                                    Instant createdAt, Instant updatedAt) {
    public static CertificationResponse from(Certification entity) {
        return new CertificationResponse(entity.getId(), entity.getName(), entity.getIssuer(),
                entity.getAcquiredDate(), entity.getExpirationDate(), entity.getCredentialId(),
                entity.getDescription(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
