package io.lonmstalker.task.api.store;

import com.google.common.base.Preconditions;

/**
 * Snapshot of task counts by status.
 */
public record TaskStoreStats(
    long total,
    long pending,
    long running,
    long waitingRetry,
    long completed,
    long failed,
    long cancelled
) {

    public TaskStoreStats {
        Preconditions.checkArgument(total >= 0, "total must be >= 0");
        Preconditions.checkArgument(pending >= 0, "pending must be >= 0");
        Preconditions.checkArgument(running >= 0, "running must be >= 0");
        Preconditions.checkArgument(waitingRetry >= 0, "waitingRetry must be >= 0");
        Preconditions.checkArgument(completed >= 0, "completed must be >= 0");
        Preconditions.checkArgument(failed >= 0, "failed must be >= 0");
        Preconditions.checkArgument(cancelled >= 0, "cancelled must be >= 0");
    }
}
