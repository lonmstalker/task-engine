package io.lonmstalker.task.kafka;

import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventRecord;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Kafka message payload with event metadata and contexts.
 */
public record TaskEventEnvelope(
    @NonNull TaskEventRecord event,
    @NonNull List<TaskEventContextEntry> contexts
) {

    public TaskEventEnvelope {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(contexts, "contexts");
        contexts = List.copyOf(contexts);
    }
}
