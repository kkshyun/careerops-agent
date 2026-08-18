package com.careerops.backend.pkbimport;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "import_batches")
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id", nullable = false)
    private SourceDocument sourceDocument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportBatchStatus status;

    @CreationTimestamp
    private Instant createdAt;

    private Instant completedAt;

    protected ImportBatch() {}

    public ImportBatch(SourceDocument sourceDocument, ImportBatchStatus status) {
        this.sourceDocument = sourceDocument;
        this.status = status;
    }

    public Long getId() { return id; }
    public SourceDocument getSourceDocument() { return sourceDocument; }
    public ImportBatchStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
