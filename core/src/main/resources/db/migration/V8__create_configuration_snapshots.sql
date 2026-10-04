CREATE TABLE edb_configuration_snapshots (
                                             id BIGINT NOT NULL AUTO_INCREMENT,

                                             project_id INT NOT NULL,

                                             version INT NOT NULL,

                                             created_at TIMESTAMP(6) NOT NULL,

                                             created_by VARCHAR(255) NOT NULL,

                                             description VARCHAR(1000) NULL,

                                             yaml TEXT NOT NULL,

                                             PRIMARY KEY (id),

                                             CONSTRAINT uk_snapshot_project_version
                                                 UNIQUE (project_id, version),

                                             INDEX idx_snapshot_project
                                                 (project_id),

                                             INDEX idx_snapshot_project_version
                                                 (project_id, version)
);