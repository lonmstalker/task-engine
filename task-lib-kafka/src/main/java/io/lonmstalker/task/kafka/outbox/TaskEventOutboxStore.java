package io.lonmstalker.task.kafka.outbox;

import io.lonmstalker.task.api.annotations.Blocking;
import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import java.time.Instant;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Outbox access for task events.
 */
public interface TaskEventOutboxStore extends AutoCloseable {

    @Blocking
    @NonNull List<TaskEventRecord> claim(
        @NonNull TaskEventOutboxClaim claim
    );

    @Blocking
    @NonNull List<TaskEventContextEntry> loadContexts(
        @NonNull TaskEventId eventId
    );

    @Blocking
    void markPublished(
        @NonNull TaskEventId eventId,
        @NonNull String leaseOwner,
        @NonNull Instant publishedAt
    );

    @Blocking
    void release(
        @NonNull TaskEventId eventId,
        @NonNull String leaseOwner,
        @NonNull Instant releasedAt
    );

    @Override
    void close();
}
