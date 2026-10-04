ALTER TABLE tasks
    MODIFY COLUMN status ENUM(
        'PENDING',
        'QUEUED',
        'RUNNING',
        'RETRYING',
        'COMPLETED',
        'FAILED',
        'TIMED_OUT',
        'CANCELLED'
    ) NOT NULL DEFAULT 'PENDING';
