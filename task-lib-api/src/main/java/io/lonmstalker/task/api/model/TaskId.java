package io.lonmstalker.task.api.model;

import java.util.Objects;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Unique identifier for a task instance.
 */
public record TaskId(
    @NonNull UUID value
) {

    public TaskId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Creates random task id.
     *
     * @return task id
     */
    public static @NonNull TaskId random() {
        return new TaskId(UUID.randomUUID());
    }

    /**
     * Parses task id from string.
     *
     * @param value id string
     * @return task id
     */
    public static @NonNull TaskId fromString(
        @NonNull String value
    ) {
        Objects.requireNonNull(value, "value");

        return new TaskId(UUID.fromString(value));
    }
}
