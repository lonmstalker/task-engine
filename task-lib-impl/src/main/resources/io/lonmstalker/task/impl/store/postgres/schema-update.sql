-- Incremental schema updates for existing installations.
CREATE INDEX IF NOT EXISTS task_tasks_claim_idx
    ON task_tasks (task_status, next_run_at, created_at);

CREATE INDEX IF NOT EXISTS task_links_depends_idx
    ON task_links (linked_task_id)
    WHERE link_type = 'DEPENDS_ON';
