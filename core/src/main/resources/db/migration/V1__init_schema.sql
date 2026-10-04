CREATE TABLE IF NOT EXISTS users (
                                     id INT PRIMARY KEY AUTO_INCREMENT,
                                     username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    employee_id VARCHAR(50) UNIQUE,
    role ENUM('ADMIN', 'MEMBER') NOT NULL DEFAULT 'ADMIN',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    );


CREATE TABLE IF NOT EXISTS projects (
                                        id INT PRIMARY KEY AUTO_INCREMENT,
                                        name VARCHAR(200) NOT NULL,
    description TEXT,
    owner_id INT NOT NULL,
    mode ENUM('PERSONAL','COLLABORATIVE') NOT NULL DEFAULT 'PERSONAL',
    config_yaml TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_projects_owner
    FOREIGN KEY(owner_id)
    REFERENCES users(id)
                                                            ON DELETE RESTRICT
    );

CREATE INDEX idx_projects_owner
    ON projects(owner_id);


CREATE TABLE IF NOT EXISTS tasks (
                                     id INT PRIMARY KEY AUTO_INCREMENT,
                                     project_id INT NOT NULL,
                                     task_type VARCHAR(50) NOT NULL,
    status ENUM('PENDING','RUNNING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    config_snapshot JSON,
    trigger_user_id INT NOT NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    result_path VARCHAR(500),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_tasks_project
    FOREIGN KEY(project_id)
    REFERENCES projects(id)
                                                            ON DELETE CASCADE,

    CONSTRAINT fk_tasks_user
    FOREIGN KEY(trigger_user_id)
    REFERENCES users(id)
                                                            ON DELETE RESTRICT
    );

CREATE INDEX idx_tasks_project
    ON tasks(project_id);

CREATE INDEX idx_tasks_status
    ON tasks(status);

CREATE INDEX idx_tasks_user
    ON tasks(trigger_user_id);


CREATE TABLE IF NOT EXISTS task_metrics (
                                            id INT PRIMARY KEY AUTO_INCREMENT,
                                            task_id INT NOT NULL,
                                            metric_name VARCHAR(50) NOT NULL,
    metric_value DOUBLE,

    CONSTRAINT fk_metrics_task
    FOREIGN KEY(task_id)
    REFERENCES tasks(id)
    ON DELETE CASCADE
    );

CREATE INDEX idx_metrics_task
    ON task_metrics(task_id);


CREATE TABLE IF NOT EXISTS data_files (
                                          id INT PRIMARY KEY AUTO_INCREMENT,
                                          task_id INT,
                                          file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    uploaded_by INT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_datafiles_task
    FOREIGN KEY(task_id)
    REFERENCES tasks(id)
    ON DELETE CASCADE,

    CONSTRAINT fk_datafiles_user
    FOREIGN KEY(uploaded_by)
    REFERENCES users(id)
    ON DELETE RESTRICT
    );

CREATE INDEX idx_datafiles_task
    ON data_files(task_id);

CREATE INDEX idx_datafiles_user
    ON data_files(uploaded_by);


CREATE TABLE IF NOT EXISTS audit_logs (
                                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                          user_id INT NOT NULL,
                                          action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    details JSON,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    status ENUM('SUCCESS', 'FAILED') NOT NULL DEFAULT 'SUCCESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_user
    FOREIGN KEY(user_id)
    REFERENCES users(id)
    ON DELETE RESTRICT
    );

CREATE INDEX idx_audit_user
    ON audit_logs(user_id);

CREATE INDEX idx_audit_action
    ON audit_logs(action);

CREATE INDEX idx_audit_created
    ON audit_logs(created_at);