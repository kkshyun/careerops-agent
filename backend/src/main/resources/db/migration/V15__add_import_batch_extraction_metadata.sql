ALTER TABLE import_batches
  ADD COLUMN extracted_at TIMESTAMP,
  ADD COLUMN extraction_provider VARCHAR(50),
  ADD COLUMN extraction_model VARCHAR(100),
  ADD COLUMN extraction_prompt_version VARCHAR(50);
