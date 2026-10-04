CREATE TABLE task_checkpoints (
    task_id INT NOT NULL,
    handler_version VARCHAR(100) NOT NULL,
    cursor VARCHAR(500) NOT NULL,
    artifacts TEXT NOT NULL,
    state TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (task_id),
    CONSTRAINT fk_task_checkpoints_task
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);
