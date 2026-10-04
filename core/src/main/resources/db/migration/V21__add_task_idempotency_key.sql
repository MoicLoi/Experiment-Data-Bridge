ALTER TABLE tasks ADD COLUMN idempotency_key VARCHAR(191) NULL;
CREATE UNIQUE INDEX uq_task_idempotency_key ON tasks(idempotency_key);
