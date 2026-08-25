--liquibase formatted sql

--changeset alfoll:012-create-task-template
CREATE TABLE task_template (
    id uuid PRIMARY KEY,
    title varchar(120) NOT NULL,
    description text,
    reward integer NOT NULL,
    created_at timestamp NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    household_id uuid NOT NULL,
    created_by uuid NOT NULL,

    CONSTRAINT ck_task_template_reward_range
        CHECK (reward BETWEEN 5 AND 100),

    CONSTRAINT fk_task_template_household
        FOREIGN KEY (household_id)
            REFERENCES household (id),

    CONSTRAINT fk_task_template_created_by
        FOREIGN KEY (created_by)
            REFERENCES "user" (id)
);

CREATE INDEX idx_task_template_household_active_created_at
    ON task_template (household_id, created_at DESC)
    WHERE is_active = true;
