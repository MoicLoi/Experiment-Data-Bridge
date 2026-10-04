ALTER TABLE tasks
    ADD COLUMN priority INT NOT NULL DEFAULT 50 AFTER status;

CREATE INDEX idx_tasks_queue
    ON tasks(status, priority DESC, created_at ASC, id ASC);
