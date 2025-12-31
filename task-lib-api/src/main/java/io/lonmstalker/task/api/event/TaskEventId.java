package io.lonmstalker.task.api.event;

import java.util.Objects;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Unique identifier for a task event.
 */
public record TaskEventId(
    @NonNull UUID value
) {

    public TaskEventId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Creates random event id.
     *
     * @return event id
     */
    public static @NonNull TaskEventId random() {
        return new TaskEventId(UUID.randomUUID());
    }

    /**
     * Parses event id from string.
     *
     * @param value id string
     * @return event id
     */
    public static @NonNull TaskEventId fromString(
        @NonNull String value
    ) {
        Objects.requireNonNull(value, "value");

        return new TaskEventId(UUID.fromString(value));
    }
}
