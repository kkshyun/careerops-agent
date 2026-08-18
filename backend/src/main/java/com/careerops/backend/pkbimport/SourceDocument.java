package com.careerops.backend.pkbimport;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "source_documents")
public class SourceDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "raw_text", nullable = false, length = 50000)
    private String rawText;

    @CreationTimestamp
    private Instant createdAt;

    protected SourceDocument() {}

    public SourceDocument(String fileName, DocumentType documentType, String contentHash, String rawText) {
        this.fileName = fileName;
        this.documentType = documentType;
        this.contentHash = contentHash;
        this.rawText = rawText;
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public DocumentType getDocumentType() { return documentType; }
    public String getContentHash() { return contentHash; }
    public String getRawText() { return rawText; }
    public Instant getCreatedAt() { return createdAt; }
}
