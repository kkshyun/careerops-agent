ALTER TABLE job_postings
    ADD CONSTRAINT uk_job_postings_source_external_id UNIQUE (source, external_id);
