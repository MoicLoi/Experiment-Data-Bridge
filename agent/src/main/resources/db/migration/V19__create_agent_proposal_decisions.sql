CREATE TABLE agent_proposal_decisions (
    proposal_id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    decided_by INT NOT NULL,
    workflow_sha256 CHAR(64) NOT NULL,
    reason TEXT NULL,
    decided_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_agent_decision_proposal FOREIGN KEY (proposal_id) REFERENCES agent_workflow_proposals(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_decision_user FOREIGN KEY (decided_by) REFERENCES users(id)
);

CREATE INDEX idx_agent_decision_actor_created ON agent_proposal_decisions(decided_by, decided_at);
