ALTER TABLE career_experiences
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_import_candidate_id BIGINT REFERENCES import_candidates(id);
ALTER TABLE career_certifications
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_import_candidate_id BIGINT REFERENCES import_candidates(id);
ALTER TABLE career_educations
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_import_candidate_id BIGINT REFERENCES import_candidates(id);
ALTER TABLE career_awards
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_import_candidate_id BIGINT REFERENCES import_candidates(id);
