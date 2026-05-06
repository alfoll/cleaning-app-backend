--liquibase formatted sql

--changeset alfoll:001-create-user
CREATE TABLE "user" (
                        id uuid PRIMARY KEY,
                        firebase_uid varchar(255) NOT NULL,
                        email varchar(255) NOT NULL,
                        name varchar(255) NOT NULL,
                        avatar_url varchar(255),
                        created_at timestamp NOT NULL,
                        is_active boolean NOT NULL default true,
                        version bigint NOT NULL DEFAULT 0,

                        CONSTRAINT uk_user_firebase_uid UNIQUE (firebase_uid),
                        CONSTRAINT uk_user_email UNIQUE (email)
);

--changeset alfoll:002-create-household
CREATE TABLE household (
                           id uuid PRIMARY KEY,
                           name varchar(255) NOT NULL,
                           invite_code varchar(255) NOT NULL,
                           created_at timestamp NOT NULL,
                           is_active boolean NOT NULL default true,
                           created_by_user uuid NOT NULL,
                           version bigint NOT NULL DEFAULT 0,

                           CONSTRAINT uk_household_invite_code UNIQUE (invite_code),
                           CONSTRAINT fk_household_created_by_user
                               FOREIGN KEY (created_by_user)
                                   REFERENCES "user" (id)
);

--changeset alfoll:003-create-user-household
CREATE TABLE user_household (
                                id uuid PRIMARY KEY,
                                user_id uuid NOT NULL,
                                household_id uuid NOT NULL,
                                balance integer NOT NULL,
                                joined_at timestamp NOT NULL,
                                is_user_active boolean NOT NULL,
                                version bigint NOT NULL DEFAULT 0,

                                CONSTRAINT uk_user_household_user_id_household_id
                                    UNIQUE (user_id, household_id),

                                CONSTRAINT ck_user_household_balance_non_negative
                                    CHECK (balance >= 0),

                                CONSTRAINT fk_user_household_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES "user" (id),

                                CONSTRAINT fk_user_household_household
                                    FOREIGN KEY (household_id)
                                        REFERENCES household (id)
);

--changeset alfoll:004-create-task
CREATE TABLE task (
                      id uuid PRIMARY KEY,
                      household_id uuid NOT NULL,
                      created_by uuid NOT NULL,
                      created_at timestamp NOT NULL,
                      title varchar(120) NOT NULL,
                      description text,
                      reward integer NOT NULL,
                      assigned_to uuid,
                      assigned_at timestamp,
                      is_completed boolean NOT NULL,
                      completed_by uuid,
                      completed_at timestamp,
                      version bigint NOT NULL DEFAULT 0,

                      CONSTRAINT ck_task_reward_range
                          CHECK (reward >= 5 AND reward <= 100),

--                       CONSTRAINT ck_task_assignment_consistency
--                           CHECK (
--                               (assigned_to IS NULL AND assigned_at IS NULL)
--                                   OR
--                               (assigned_to IS NOT NULL AND assigned_at IS NOT NULL)
--                               ),
--
--                       CONSTRAINT ck_task_completion_consistency
--                           CHECK (
--                               (is_completed = false AND completed_by IS NULL AND completed_at IS NULL)
--                                   OR
--                               (is_completed = true AND completed_by IS NOT NULL AND completed_at IS NOT NULL)
--                               ),

                      CONSTRAINT ck_task_state_consistency
                          CHECK (
                              (
                                  is_completed = false
                                      AND completed_by IS NULL
                                      AND completed_at IS NULL
                                      AND (
                                      (assigned_to IS NULL AND assigned_at IS NULL)
                                          OR
                                      (assigned_to IS NOT NULL AND assigned_at IS NOT NULL)
                                      )
                                  )
                                  OR
                              (
                                  is_completed = true
                                      AND completed_by IS NOT NULL
                                      AND completed_at IS NOT NULL
                                      AND assigned_to IS NULL
                                      AND assigned_at IS NULL
                                  )
                              ),

                      CONSTRAINT fk_task_household
                          FOREIGN KEY (household_id)
                              REFERENCES household (id),

                      CONSTRAINT fk_task_created_by
                          FOREIGN KEY (created_by)
                              REFERENCES "user" (id),

                      CONSTRAINT fk_task_assigned_to
                          FOREIGN KEY (assigned_to)
                              REFERENCES user_household (id),

                      CONSTRAINT fk_task_completed_by
                          FOREIGN KEY (completed_by)
                              REFERENCES user_household (id)
);

--changeset alfoll:005-create-privilege
CREATE TABLE privilege (
                           id uuid PRIMARY KEY,
                           household_id uuid NOT NULL,
                           created_by uuid NOT NULL,
                           created_at timestamp NOT NULL,
                           title varchar(120) NOT NULL,
                           description text,
                           cost integer NOT NULL,
                           is_available boolean NOT NULL,
                           bought_by uuid,
                           version bigint NOT NULL DEFAULT 0,

                           CONSTRAINT ck_privilege_cost_range
                               CHECK (cost >= 5 AND cost <= 500),

                           CONSTRAINT ck_privilege_availability_consistency
                               CHECK (
                                   (is_available = true AND bought_by IS NULL)
                                       OR
                                   (is_available = false AND bought_by IS NOT NULL)
                                   ),

                           CONSTRAINT fk_privilege_household
                               FOREIGN KEY (household_id)
                                   REFERENCES household (id),

                           CONSTRAINT fk_privilege_created_by
                               FOREIGN KEY (created_by)
                                   REFERENCES "user" (id),

                           CONSTRAINT fk_privilege_bought_by
                               FOREIGN KEY (bought_by)
                                   REFERENCES user_household (id)
);

--changeset alfoll:006-create-transaction
CREATE TABLE "transaction" (
                               id uuid PRIMARY KEY,
                               household_id uuid NOT NULL,
                               member_id uuid NOT NULL,
                               amount integer NOT NULL,
                               created_at timestamp NOT NULL,
                               type varchar(32) NOT NULL,
                               task_id uuid,
                               privilege_id uuid,

                               CONSTRAINT uk_transaction_task_id UNIQUE (task_id),
                               CONSTRAINT uk_transaction_privilege_id UNIQUE (privilege_id),

                               CONSTRAINT ck_transaction_payload_by_type
                                   CHECK (
                                       (
                                           type = 'TASK_COMPLETION'
                                               AND amount > 0
                                               AND task_id IS NOT NULL
                                               AND privilege_id IS NULL
                                           )
                                           OR
                                       (
                                           type = 'PRIVILEGE_BOUGHT'
                                               AND amount < 0
                                               AND task_id IS NULL
                                               AND privilege_id IS NOT NULL
                                           )
                                           OR
                                       (
                                           type = 'BALANCE_RESET'
                                               AND amount < 0
                                               AND task_id IS NULL
                                               AND privilege_id IS NULL
                                           )
                                       ),

                               CONSTRAINT fk_transaction_household
                                   FOREIGN KEY (household_id)
                                       REFERENCES household (id),

                               CONSTRAINT fk_transaction_member
                                   FOREIGN KEY (member_id)
                                       REFERENCES user_household (id),

                               CONSTRAINT fk_transaction_task
                                   FOREIGN KEY (task_id)
                                       REFERENCES task (id),

                               CONSTRAINT fk_transaction_privilege
                                   FOREIGN KEY (privilege_id)
                                       REFERENCES privilege (id)
);

--changeset alfoll:007-create-activity
CREATE TABLE activity (
                          id uuid PRIMARY KEY,
                          household_id uuid NOT NULL,
                          member_id uuid NOT NULL,
                          activity_type varchar(40) NOT NULL,
                          title varchar(180) NOT NULL,
                          description text,
                          created_at timestamp NOT NULL,

                          CONSTRAINT fk_activity_household
                              FOREIGN KEY (household_id)
                                  REFERENCES household (id),

                          CONSTRAINT fk_activity_member
                              FOREIGN KEY (member_id)
                                  REFERENCES user_household (id)
);

--changeset alfoll:008-create-indexes
CREATE INDEX idx_user_household_user_active
    ON user_household (user_id, is_user_active);

CREATE INDEX idx_user_household_household_active
    ON user_household (household_id, is_user_active);

CREATE INDEX idx_task_household_created_at
    ON task (household_id, created_at);

CREATE INDEX idx_task_household_completed
    ON task (household_id, is_completed);

CREATE INDEX idx_task_assigned_to
    ON task (assigned_to);

CREATE INDEX idx_privilege_household_created_at
    ON privilege (household_id, created_at);

CREATE INDEX idx_privilege_household_available
    ON privilege (household_id, is_available);

CREATE INDEX idx_privilege_bought_by
    ON privilege (bought_by);

CREATE INDEX idx_transaction_household_id_created_at
    ON "transaction" (household_id, created_at);

CREATE INDEX idx_transaction_member_id_created_at
    ON "transaction" (member_id, created_at);

CREATE INDEX idx_transaction_leaderboard
    ON "transaction" (household_id, type, member_id, created_at);

CREATE INDEX idx_activity_household_created_at
    ON activity (household_id, created_at);

CREATE INDEX idx_activity_household_type_created_at
    ON activity (household_id, activity_type, created_at);

CREATE INDEX idx_activity_household_member_created_at
    ON activity (household_id, member_id, created_at);