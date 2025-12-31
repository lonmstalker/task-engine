package io.lonmstalker.task.api.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Immutable view of a task.
 */
public record TaskSnapshot(
    @NonNull TaskId id,
    @NonNull TaskKey key,
    @NonNull TaskType type,
    @NonNull TaskState state,
    @NonNull TaskStatus status,
    int attempt,
    int maxAttempts,
    @Nullable Instant nextRunAt,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt,
    @NonNull TaskPayload payload,
    @NonNull List<TaskLink> links
) {

    public TaskSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(links, "links");
        links = List.copyOf(links);
    }
}
