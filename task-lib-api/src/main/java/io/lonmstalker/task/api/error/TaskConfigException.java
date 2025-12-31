package io.lonmstalker.task.api.error;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Configuration error.
 */
public final class TaskConfigException extends TaskException {

    public TaskConfigException(
        @NonNull String message
    ) {
        super(Objects.requireNonNull(message, "message"));
    }

    public TaskConfigException(
        @NonNull String message,
        @NonNull Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    }
}
