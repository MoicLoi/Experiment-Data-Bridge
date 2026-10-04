CREATE TABLE IF NOT EXISTS configuration_metadata (
                                        project_id INT NOT NULL,
                                        description TEXT NULL,
                                        category VARCHAR(128) NULL,
                                        tags TEXT NOT NULL,
                                        updated_at TIMESTAMP NOT NULL,
                                        source VARCHAR(128) NULL,

                                        CONSTRAINT pk_configuration_metadata
                                            PRIMARY KEY (project_id)
);