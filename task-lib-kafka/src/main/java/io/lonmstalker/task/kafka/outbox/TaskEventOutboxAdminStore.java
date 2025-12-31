package io.lonmstalker.task.kafka.outbox;

import io.lonmstalker.task.api.annotations.Blocking;
import io.lonmstalker.task.api.event.TaskEventId;
import java.time.Instant;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Optional extension for outbox stores with administration capabilities.
 */
public interface TaskEventOutboxAdminStore {

    @Blocking
    int loadPublishAttempts(
        @NonNull TaskEventId eventId
    );

    @Blocking
    void markDeadLetter(
        @NonNull TaskEventId eventId,
        @NonNull String leaseOwner,
        @NonNull Instant deadLetterAt,
        @NonNull String reason
    );
}
