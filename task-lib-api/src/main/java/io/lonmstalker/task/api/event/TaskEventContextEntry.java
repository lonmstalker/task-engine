package io.lonmstalker.task.api.event;

import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskType;
import java.time.Instant;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Event context payload entry.
 */
public record TaskEventContextEntry(
    @NonNull TaskId taskId,
    @NonNull TaskType taskType,
    @NonNull TaskEventContextKind kind,
    @NonNull TaskPayload payload,
    @NonNull Instant createdAt
) {

    public TaskEventContextEntry {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
