-- Incremental schema updates for existing installations.
CREATE INDEX IF NOT EXISTS task_tasks_claim_idx
    ON task_tasks (task_status, next_run_at, created_at);

CREATE INDEX IF NOT EXISTS task_links_depends_idx
    ON task_links (linked_task_id)
    WHERE link_type = 'DEPENDS_ON';

ALTER TABLE task_event_outbox
    ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(128) NULL;

ALTER TABLE task_event_outbox
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ NULL;

ALTER TABLE task_event_outbox
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ NULL;

ALTER TABLE task_event_outbox
    ADD COLUMN IF NOT EXISTS publish_attempts INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS task_event_outbox_pending_idx
    ON task_event_outbox (lease_until, created_at)
    WHERE published_at IS NULL;
