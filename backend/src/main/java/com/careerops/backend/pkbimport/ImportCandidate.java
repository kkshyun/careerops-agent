package com.careerops.backend.pkbimport;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "import_candidates")
public class ImportCandidate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id", nullable = false) private ImportBatch importBatch;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 30)
    private CandidateTargetType targetType;
    @Column(nullable = false, length = 10000) private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ImportCandidateStatus status;
    @Column(name = "created_entity_id") private Long createdEntityId;
    @CreationTimestamp private Instant createdAt;
    private Instant reviewedAt;

    protected ImportCandidate() {}
    public ImportCandidate(ImportBatch importBatch, CandidateTargetType targetType, String payload) {
        this.importBatch = importBatch; this.targetType = targetType; this.payload = payload;
        this.status = ImportCandidateStatus.PENDING;
    }
    public Long getId() { return id; }
    public ImportBatch getImportBatch() { return importBatch; }
    public CandidateTargetType getTargetType() { return targetType; }
    public String getPayload() { return payload; }
    public ImportCandidateStatus getStatus() { return status; }
    public Long getCreatedEntityId() { return createdEntityId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setCreatedEntityId(Long value) { createdEntityId = value; }
}
