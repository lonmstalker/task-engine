package io.lonmstalker.task.kafka.outbox;

import io.lonmstalker.task.api.annotations.Blocking;
import java.time.Instant;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Optional maintenance operations for outbox stores.
 */
public interface TaskEventOutboxMaintenanceStore {

    /**
     * Purges published or dead-lettered events older than the provided timestamp.
     *
     * @param olderThan cutoff timestamp
     * @return number of deleted events
     */
    @Blocking
    int purgePublishedEvents(
        @NonNull Instant olderThan
    );
}
