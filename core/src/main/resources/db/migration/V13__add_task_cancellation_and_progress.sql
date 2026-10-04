ALTER TABLE tasks
    ADD COLUMN cancellation_requested_at TIMESTAMP NULL AFTER timeout_ms,
    ADD COLUMN cancellation_requested_by INT NULL AFTER cancellation_requested_at,
    ADD COLUMN progress_percent INT NULL AFTER cancellation_requested_by,
    ADD COLUMN progress_message VARCHAR(500) NULL AFTER progress_percent,
    ADD COLUMN progress_updated_at TIMESTAMP NULL AFTER progress_message,
    ADD CONSTRAINT fk_tasks_cancellation_user
        FOREIGN KEY (cancellation_requested_by)
        REFERENCES users(id)
        ON DELETE RESTRICT;

CREATE INDEX idx_tasks_cancellation
    ON tasks(status, cancellation_requested_at);
