ALTER TABLE data_files
    ADD COLUMN project_id INT NULL,
    ADD COLUMN kind VARCHAR(30) NOT NULL DEFAULT 'RAW_DATA',
    ADD COLUMN format VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN media_type VARCHAR(255) NULL,
    ADD COLUMN size_bytes BIGINT NULL,
    ADD COLUMN storage_provider VARCHAR(50) NOT NULL DEFAULT 'local',
    ADD COLUMN storage_key VARCHAR(500) NULL,
    ADD COLUMN origin VARCHAR(30) NOT NULL DEFAULT 'UPLOAD',
    ADD COLUMN external_system VARCHAR(100) NULL,
    ADD COLUMN metadata_json LONGTEXT NULL,
    ADD COLUMN updated_at TIMESTAMP NULL;

UPDATE data_files df
LEFT JOIN tasks task_record ON task_record.id = df.task_id
SET df.project_id = task_record.project_id
WHERE df.project_id IS NULL AND df.task_id IS NOT NULL;

UPDATE data_files
SET storage_key = file_path,
    metadata_json = '{}',
    updated_at = uploaded_at
WHERE storage_key IS NULL OR metadata_json IS NULL OR updated_at IS NULL;

ALTER TABLE data_files
    MODIFY COLUMN storage_key VARCHAR(500) NOT NULL,
    MODIFY COLUMN metadata_json LONGTEXT NOT NULL,
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL,
    ADD CONSTRAINT fk_datafiles_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

CREATE INDEX idx_datafiles_project_created
    ON data_files(project_id, uploaded_at);

CREATE INDEX idx_datafiles_status
    ON data_files(status);

CREATE INDEX idx_datafiles_hash
    ON data_files(file_hash);

CREATE TABLE data_file_parents (
    child_data_file_id INT NOT NULL,
    parent_data_file_id INT NOT NULL,
    PRIMARY KEY (child_data_file_id, parent_data_file_id),
    CONSTRAINT fk_datafile_parents_child
        FOREIGN KEY (child_data_file_id) REFERENCES data_files(id) ON DELETE CASCADE,
    CONSTRAINT fk_datafile_parents_parent
        FOREIGN KEY (parent_data_file_id) REFERENCES data_files(id) ON DELETE CASCADE
);

CREATE INDEX idx_datafile_parents_parent
    ON data_file_parents(parent_data_file_id);
