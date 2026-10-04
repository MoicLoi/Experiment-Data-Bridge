CREATE TABLE IF NOT EXISTS audit_logs
(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id INT NOT NULL,

    action VARCHAR(100) NOT NULL,

    resource_type VARCHAR(100) NOT NULL,

    resource_id INT,

    details TEXT,

    ip_address VARCHAR(45) NULL,

    user_agent VARCHAR(512) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,


    CONSTRAINT fk_audit_user
        FOREIGN KEY(user_id)
            REFERENCES users(id)

);