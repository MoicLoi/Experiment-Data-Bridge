CREATE TABLE agent_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id INT NOT NULL,
    project_id INT NOT NULL,
    agent_id VARCHAR(100) NOT NULL,
    agent_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_agent_session_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_agent_session_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX idx_agent_session_actor ON agent_sessions(user_id, project_id, updated_at);

CREATE TABLE agent_runs (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    model_profile_id VARCHAR(100) NOT NULL,
    input_tokens BIGINT NULL,
    output_tokens BIGINT NULL,
    failure_code VARCHAR(100) NULL,
    failure_message TEXT NULL,
    failure_retryable BOOLEAN NULL,
    started_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_agent_run_session FOREIGN KEY (session_id) REFERENCES agent_sessions(id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_run_session_created ON agent_runs(session_id, started_at);
CREATE INDEX idx_agent_run_status ON agent_runs(status, updated_at);

CREATE TABLE agent_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    tool_call_id VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id) REFERENCES agent_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_message_run FOREIGN KEY (run_id) REFERENCES agent_runs(id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_message_run_created ON agent_messages(run_id, created_at, id);

CREATE TABLE agent_tool_calls (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    tool_call_id VARCHAR(100) NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    capability VARCHAR(40) NOT NULL,
    arguments_json LONGTEXT NOT NULL,
    result_json LONGTEXT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(100) NULL,
    error_message TEXT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_agent_tool_run FOREIGN KEY (run_id) REFERENCES agent_runs(id) ON DELETE CASCADE,
    CONSTRAINT uq_agent_tool_call UNIQUE (run_id, tool_call_id)
);

CREATE INDEX idx_agent_tool_run_created ON agent_tool_calls(run_id, started_at, id);

CREATE TABLE agent_workflow_proposals (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    workflow_ir_json LONGTEXT NOT NULL,
    explanation TEXT NOT NULL,
    assumptions_json LONGTEXT NOT NULL,
    warnings_json LONGTEXT NOT NULL,
    validation_valid BOOLEAN NOT NULL,
    execution_order_json LONGTEXT NOT NULL,
    validation_errors_json LONGTEXT NOT NULL,
    approval_required BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_agent_proposal_run FOREIGN KEY (run_id) REFERENCES agent_runs(id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_proposal_run_created ON agent_workflow_proposals(run_id, created_at);

CREATE TABLE agent_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NULL,
    actor_user_id INT NOT NULL,
    project_id INT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    run_status VARCHAR(30) NULL,
    details_json LONGTEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_agent_audit_session FOREIGN KEY (session_id) REFERENCES agent_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_audit_run FOREIGN KEY (run_id) REFERENCES agent_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT fk_agent_audit_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX idx_agent_audit_run_created ON agent_audit_events(run_id, created_at, id);
CREATE INDEX idx_agent_audit_actor_created ON agent_audit_events(actor_user_id, project_id, created_at);
