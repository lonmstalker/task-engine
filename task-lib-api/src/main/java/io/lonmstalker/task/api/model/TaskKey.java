package io.lonmstalker.task.api.model;

import com.google.common.base.Preconditions;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Idempotency key for a task.
 */
public record TaskKey(
    @NonNull String value
) {

    public TaskKey {
        Objects.requireNonNull(value, "value");
        Preconditions.checkArgument(!value.isBlank(), "TaskKey must not be blank");
        Preconditions.checkArgument(value.length() <= 255, "TaskKey length must be <= 255");
    }

    /**
     * Creates task key.
     *
     * @param value key value
     * @return task key
     */
    public static @NonNull TaskKey of(
        @NonNull String value
    ) {
        return new TaskKey(value);
    }
}
