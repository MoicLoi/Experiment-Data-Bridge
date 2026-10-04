CREATE TABLE task_audit_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id INT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NULL,
    actor_user_id INT NULL,
    details JSON NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_task_audit_task
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_audit_actor
        FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_task_audit_task_created
    ON task_audit_events(task_id, created_at, id);

CREATE INDEX idx_task_audit_type
    ON task_audit_events(event_type);
