CREATE TABLE IF NOT EXISTS task_tasks (
    id UUID PRIMARY KEY,
    task_key VARCHAR(255) NOT NULL UNIQUE,
    task_type VARCHAR(255) NOT NULL,
    task_state VARCHAR(255) NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    attempt INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    next_run_at TIMESTAMPTZ NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMPTZ NULL,
    payload BYTEA NOT NULL,
    payload_content_type VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_error_type VARCHAR(255) NULL,
    last_error_message TEXT NULL
);

CREATE INDEX IF NOT EXISTS task_tasks_status_idx
    ON task_tasks (task_status, next_run_at);

CREATE INDEX IF NOT EXISTS task_tasks_lease_idx
    ON task_tasks (lease_until);

CREATE TABLE IF NOT EXISTS task_links (
    task_id UUID NOT NULL REFERENCES task_tasks(id) ON DELETE CASCADE,
    linked_task_id UUID NOT NULL REFERENCES task_tasks(id) ON DELETE CASCADE,
    link_type VARCHAR(32) NOT NULL,
    PRIMARY KEY (task_id, linked_task_id, link_type)
);

CREATE INDEX IF NOT EXISTS task_links_linked_idx
    ON task_links (linked_task_id);

CREATE TABLE IF NOT EXISTS task_event_outbox (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES task_tasks(id) ON DELETE CASCADE,
    task_key VARCHAR(255) NOT NULL,
    task_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS task_event_outbox_task_idx
    ON task_event_outbox (task_id, created_at);

CREATE TABLE IF NOT EXISTS task_event_contexts (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NULL REFERENCES task_event_outbox(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES task_tasks(id) ON DELETE CASCADE,
    task_type VARCHAR(255) NOT NULL,
    context_kind VARCHAR(32) NOT NULL,
    payload BYTEA NOT NULL,
    payload_content_type VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS task_event_contexts_event_idx
    ON task_event_contexts (event_id);

CREATE INDEX IF NOT EXISTS task_event_contexts_task_idx
    ON task_event_contexts (task_id, event_id);

CREATE INDEX IF NOT EXISTS task_event_contexts_pending_idx
    ON task_event_contexts (task_id)
    WHERE event_id IS NULL;
