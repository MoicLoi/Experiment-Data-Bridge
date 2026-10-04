ALTER TABLE tasks
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER priority,
    ADD COLUMN max_attempts INT NOT NULL DEFAULT 1 AFTER attempt_count,
    ADD COLUMN retry_initial_delay_ms BIGINT NOT NULL DEFAULT 1000 AFTER max_attempts,
    ADD COLUMN retry_backoff_multiplier DOUBLE NOT NULL DEFAULT 2.0 AFTER retry_initial_delay_ms,
    ADD COLUMN retry_max_delay_ms BIGINT NOT NULL DEFAULT 60000 AFTER retry_backoff_multiplier,
    ADD COLUMN next_attempt_at TIMESTAMP NULL AFTER retry_max_delay_ms,
    ADD COLUMN timeout_ms BIGINT NULL AFTER next_attempt_at;

UPDATE tasks
SET attempt_count = 1
WHERE status IN ('RUNNING', 'COMPLETED', 'FAILED', 'TIMED_OUT');

CREATE INDEX idx_tasks_retry
    ON tasks(status, next_attempt_at);
