package io.lonmstalker.task.api.store;

import io.lonmstalker.task.api.model.TaskErrorInfo;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskType;
import java.time.Instant;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Persisted task record.
 */
public record TaskRecord(
    @NonNull TaskId id,
    @NonNull TaskKey key,
    @NonNull TaskType type,
    @NonNull TaskState state,
    @NonNull TaskStatus status,
    int attempt,
    int maxAttempts,
    @Nullable Instant nextRunAt,
    @Nullable String leaseOwner,
    @Nullable Instant leaseUntil,
    @NonNull TaskPayload payload,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt,
    @Nullable TaskErrorInfo lastError
) {

    public TaskRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
