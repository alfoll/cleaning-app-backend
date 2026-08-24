--liquibase formatted sql

--changeset alfoll:010-add-task-due-at
ALTER TABLE task
    ADD COLUMN due_at TIMESTAMP NULL;
