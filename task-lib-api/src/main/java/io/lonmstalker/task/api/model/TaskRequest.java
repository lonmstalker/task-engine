package io.lonmstalker.task.api.model;

import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Request to submit a task.
 */
public record TaskRequest<C>(
    @NonNull TaskKey key,
    @NonNull TaskType type,
    @NonNull TaskState state,
    @Nullable String name,
    @NonNull C context,
    @NonNull List<TaskLink> links
) {

    public TaskRequest {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(links, "links");
        links = List.copyOf(links);
    }

    /**
     * Creates task request without links.
     *
     * @param key idempotency key
     * @param type task type
     * @param state task state
     * @param context task context
     * @return task request
     */
    public static <C> @NonNull TaskRequest<C> of(
        @NonNull TaskKey key,
        @NonNull TaskType type,
        @NonNull TaskState state,
        @NonNull C context
    ) {
        return new TaskRequest<>(key, type, state, null, context, List.of());
    }

    /**
     * Creates task request with name.
     *
     * @param key idempotency key
     * @param type task type
     * @param state task state
     * @param name human-readable task name
     * @param context task context
     * @return task request
     */
    public static <C> @NonNull TaskRequest<C> of(
        @NonNull TaskKey key,
        @NonNull TaskType type,
        @NonNull TaskState state,
        @Nullable String name,
        @NonNull C context
    ) {
        return new TaskRequest<>(key, type, state, name, context, List.of());
    }
}
