ALTER TABLE data_files
    DROP FOREIGN KEY fk_datafiles_task;

ALTER TABLE data_files
    ADD CONSTRAINT fk_datafiles_task
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL;
