ALTER TABLE tasks
    ADD COLUMN result_content_type VARCHAR(100) NULL AFTER result_path,
    ADD COLUMN result_metadata JSON NULL AFTER result_content_type,
    ADD COLUMN error_category VARCHAR(30) NULL AFTER error_message,
    ADD COLUMN error_code VARCHAR(100) NULL AFTER error_category,
    ADD COLUMN error_retryable BOOLEAN NULL AFTER error_code,
    ADD COLUMN error_details JSON NULL AFTER error_retryable,
    ADD COLUMN error_occurred_at TIMESTAMP NULL AFTER error_details;

UPDATE tasks
SET error_category = 'EXECUTION',
    error_code = 'LEGACY_ERROR',
    error_retryable = FALSE,
    error_details = JSON_OBJECT(),
    error_occurred_at = COALESCE(completed_at, updated_at)
WHERE error_message IS NOT NULL;
