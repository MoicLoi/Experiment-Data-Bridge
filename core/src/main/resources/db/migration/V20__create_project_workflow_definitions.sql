CREATE TABLE project_workflow_definitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id INT NOT NULL,
    workflow_id VARCHAR(150) NOT NULL,
    version INT NOT NULL,
    workflow_ir_json LONGTEXT NOT NULL,
    workflow_sha256 CHAR(64) NOT NULL,
    created_by INT NOT NULL,
    source_reference VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_project_workflow_definition_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_workflow_definition_user FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT uq_project_workflow_version UNIQUE (project_id, workflow_id, version),
    CONSTRAINT uq_project_workflow_content UNIQUE (project_id, workflow_id, workflow_sha256)
);

CREATE INDEX idx_project_workflow_latest ON project_workflow_definitions(project_id, workflow_id, version);
