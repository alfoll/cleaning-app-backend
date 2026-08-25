--liquibase formatted sql

--changeset alfoll:011-add-task-due-at-index
CREATE INDEX idx_task_household_due_at_pending
    ON task (household_id, due_at)
    WHERE is_completed = false
      AND due_at IS NOT NULL;
