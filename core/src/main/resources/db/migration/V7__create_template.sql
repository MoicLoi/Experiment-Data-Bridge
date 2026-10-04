CREATE TABLE templates
(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    name VARCHAR(255) NOT NULL UNIQUE,

    description TEXT NOT NULL,

    type VARCHAR(64) NOT NULL,

    visibility VARCHAR(64) NOT NULL,

    version INT NOT NULL,

    author VARCHAR(128) NOT NULL,

    created_at TIMESTAMP NOT NULL,

    updated_at TIMESTAMP NOT NULL,

    yaml TEXT NOT NULL,

    INDEX idx_edb_templates_name (name),

    INDEX idx_edb_templates_type (type),

    INDEX idx_edb_templates_visibility (visibility),

    INDEX idx_edb_templates_created_at (created_at)
);