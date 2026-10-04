CREATE TABLE docker_registry_profiles (
 id VARCHAR(100) NOT NULL, display_name VARCHAR(160) NOT NULL, registry_url VARCHAR(500) NOT NULL,
 repository_namespace VARCHAR(255) NULL, credential_ref VARCHAR(160) NULL,
 allow_insecure BOOLEAN NOT NULL DEFAULT FALSE, is_default BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY (id), INDEX idx_docker_registry_profiles_default (is_default)
);
