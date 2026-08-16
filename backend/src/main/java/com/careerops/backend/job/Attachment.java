package com.careerops.backend.job;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "attachments", uniqueConstraints = @UniqueConstraint(name = "uk_attachments_recrut_atch_file_no", columnNames = "recrut_atch_file_no"))
public class Attachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "job_posting_id", nullable = false) private JobPosting jobPosting;
    @Column(nullable = false) private Long recrutAtchFileNo;
    private Integer sortNo;
    private String fileName;
    private String fileType;
    @Column(length = 2048) private String url;
    @CreationTimestamp private Instant createdAt;

    protected Attachment() {}
    public Attachment(JobPosting jobPosting, Long recrutAtchFileNo, Integer sortNo, String fileName, String fileType, String url) {
        this.jobPosting = jobPosting; this.recrutAtchFileNo = recrutAtchFileNo; this.sortNo = sortNo;
        this.fileName = fileName; this.fileType = fileType; this.url = url;
    }
    public Long getId() { return id; }
    public JobPosting getJobPosting() { return jobPosting; }
    public Long getRecrutAtchFileNo() { return recrutAtchFileNo; }
    public Integer getSortNo() { return sortNo; }
    public String getFileName() { return fileName; }
    public String getFileType() { return fileType; }
    public String getUrl() { return url; }
    public Instant getCreatedAt() { return createdAt; }
}
