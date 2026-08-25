--liquibase formatted sql

--changeset alfoll:013-create-task-plan
CREATE TABLE task_plan (
    id uuid PRIMARY KEY,
    household_id uuid NOT NULL,
    created_by uuid NOT NULL,
    title varchar(120) NOT NULL,
    description text,
    reward integer NOT NULL,
    recurrence_type varchar(20) NOT NULL,
    next_due_at timestamp NOT NULL,
    monthly_anchor_day integer,
    monthly_last_day boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL,
    version bigint NOT NULL DEFAULT 0,

    CONSTRAINT ck_task_plan_reward_range
        CHECK (reward BETWEEN 5 AND 100),

    CONSTRAINT ck_task_plan_recurrence_type
        CHECK (recurrence_type IN ('DAILY', 'WEEKLY', 'MONTHLY')),

    CONSTRAINT ck_task_plan_monthly_rule
        CHECK (
            (recurrence_type = 'MONTHLY' AND (
                (monthly_last_day = true AND monthly_anchor_day IS NULL)
                OR
                (monthly_last_day = false AND monthly_anchor_day BETWEEN 1 AND 31)
            ))
            OR
            (recurrence_type <> 'MONTHLY' AND monthly_anchor_day IS NULL AND monthly_last_day = false)
        ),

    CONSTRAINT fk_task_plan_household
        FOREIGN KEY (household_id)
            REFERENCES household (id),

    CONSTRAINT fk_task_plan_created_by
        FOREIGN KEY (created_by)
            REFERENCES "user" (id)
);

CREATE INDEX idx_task_plan_active_next_due_at
    ON task_plan (next_due_at)
    WHERE is_active = true;

ALTER TABLE task
    ADD COLUMN task_plan_id uuid;

ALTER TABLE task
    ADD CONSTRAINT fk_task_task_plan
        FOREIGN KEY (task_plan_id)
            REFERENCES task_plan (id);

CREATE INDEX idx_task_task_plan_id
    ON task (task_plan_id);
