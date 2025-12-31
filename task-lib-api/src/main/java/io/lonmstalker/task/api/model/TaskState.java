package io.lonmstalker.task.api.model;

import com.google.common.base.Preconditions;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Business state of a task.
 */
public record TaskState(
    @NonNull String value
) {

    public TaskState {
        Objects.requireNonNull(value, "value");
        Preconditions.checkArgument(!value.isBlank(), "TaskState must not be blank");
        Preconditions.checkArgument(value.length() <= 255, "TaskState length must be <= 255");
    }

    /**
     * Creates task state.
     *
     * @param value state value
     * @return task state
     */
    public static @NonNull TaskState of(
        @NonNull String value
    ) {
        return new TaskState(value);
    }
}
