package io.lonmstalker.task.api.event;

import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskType;
import java.time.Instant;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Stored task event.
 */
public record TaskEventRecord(
    @NonNull TaskEventId id,
    @NonNull TaskId taskId,
    @NonNull TaskKey taskKey,
    @NonNull TaskType taskType,
    @NonNull TaskEventType type,
    @NonNull Instant createdAt
) {

    public TaskEventRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(taskKey, "taskKey");
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
