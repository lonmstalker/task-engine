package io.lonmstalker.task.api.store;

import io.lonmstalker.task.api.annotations.Blocking;
import java.time.Instant;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Optional maintenance operations for task stores.
 */
public interface TaskMaintenanceStore {

    /**
     * Purges terminal tasks older than the provided timestamp.
     *
     * @param olderThan cutoff timestamp
     * @return number of deleted tasks
     */
    @Blocking
    int purgeCompletedTasks(
        @NonNull Instant olderThan
    );
}
