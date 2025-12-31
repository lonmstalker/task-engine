package io.lonmstalker.task.api.model;

import com.google.common.base.Preconditions;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Task type identifier.
 */
public record TaskType(
    @NonNull String value
) {

    public TaskType {
        Objects.requireNonNull(value, "value");
        Preconditions.checkArgument(!value.isBlank(), "TaskType must not be blank");
        Preconditions.checkArgument(value.length() <= 255, "TaskType length must be <= 255");
    }

    /**
     * Creates task type.
     *
     * @param value type value
     * @return task type
     */
    public static @NonNull TaskType of(
        @NonNull String value
    ) {
        return new TaskType(value);
    }
}
