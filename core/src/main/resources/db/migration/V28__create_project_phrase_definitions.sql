CREATE TABLE project_phrase_definitions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id INT NOT NULL,
    definition_id VARCHAR(150) NOT NULL,
    version INT NOT NULL,
    document_json TEXT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_by INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_phrase_definition_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_phrase_definition_user FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT uq_phrase_definition_version UNIQUE (project_id, definition_id, version),
    CONSTRAINT uq_phrase_definition_hash UNIQUE (project_id, definition_id, sha256)
);
CREATE INDEX idx_phrase_definition_latest ON project_phrase_definitions(project_id, definition_id, version);
