package io.lonmstalker.task.kafka.outbox;

/**
 * Snapshot of outbox counts.
 */
public record TaskEventOutboxStats(
    long pending,
    long deadLettered
) {

    public TaskEventOutboxStats {
        if (pending < 0) {
            throw new IllegalArgumentException("pending must be >= 0");
        }
        if (deadLettered < 0) {
            throw new IllegalArgumentException("deadLettered must be >= 0");
        }
    }
}
