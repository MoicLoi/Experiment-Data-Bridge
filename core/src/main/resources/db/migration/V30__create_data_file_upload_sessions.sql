CREATE TABLE data_file_upload_sessions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    project_id INT NOT NULL,
    user_id INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(255) NULL,
    total_bytes BIGINT NOT NULL,
    offset_bytes BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_upload_session_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_upload_session_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_upload_session_offsets CHECK (total_bytes >= 0 AND offset_bytes >= 0 AND offset_bytes <= total_bytes)
);
CREATE INDEX idx_upload_session_owner ON data_file_upload_sessions(project_id, user_id, status, updated_at);
