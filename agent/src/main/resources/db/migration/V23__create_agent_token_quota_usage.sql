CREATE TABLE agent_token_quota_usage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scope_type VARCHAR(20) NOT NULL,
    scope_id INT NOT NULL,
    usage_date DATE NOT NULL,
    used_tokens BIGINT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_agent_token_quota_scope_day UNIQUE (scope_type, scope_id, usage_date)
);
