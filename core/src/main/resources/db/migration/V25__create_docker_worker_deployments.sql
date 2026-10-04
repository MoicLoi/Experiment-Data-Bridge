CREATE TABLE docker_worker_deployments (
 worker_id VARCHAR(100) NOT NULL, registry_profile_id VARCHAR(100) NOT NULL,
 repository_name VARCHAR(500) NOT NULL, image_tag VARCHAR(160) NOT NULL,
 committed_image VARCHAR(800) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY (worker_id), CONSTRAINT fk_docker_worker_deployment_registry FOREIGN KEY (registry_profile_id) REFERENCES docker_registry_profiles(id)
);
