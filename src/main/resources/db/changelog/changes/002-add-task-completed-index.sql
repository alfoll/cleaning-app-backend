--liquibase formatted sql

--changeset alfoll:009-add-task-completed-feed-index
CREATE INDEX idx_task_household_completed_at_desc
    ON task (household_id, completed_at DESC)
    WHERE is_completed = true;