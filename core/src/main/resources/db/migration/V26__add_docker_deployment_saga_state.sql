ALTER TABLE docker_worker_deployments
 ADD COLUMN operation_id VARCHAR(36) NULL,
 ADD COLUMN previous_image VARCHAR(800) NULL,
 ADD COLUMN deployment_status VARCHAR(32) NOT NULL DEFAULT 'COMMITTED',
 ADD COLUMN failure_message TEXT NULL,
 ADD COLUMN started_at TIMESTAMP(6) NULL,
 ADD INDEX idx_docker_worker_deployments_status (deployment_status);
