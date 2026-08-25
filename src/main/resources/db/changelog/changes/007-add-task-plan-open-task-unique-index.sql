--liquibase formatted sql

--changeset alfoll:014-add-task-plan-open-task-unique-index
CREATE UNIQUE INDEX idx_task_task_plan_single_open
    ON task (task_plan_id)
    WHERE task_plan_id IS NOT NULL
      AND is_completed = false;
