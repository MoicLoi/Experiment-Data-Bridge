ALTER TABLE agent_messages
    ADD COLUMN memory_eligible BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_agent_messages_session_memory_created
    ON agent_messages (session_id, memory_eligible, created_at);
