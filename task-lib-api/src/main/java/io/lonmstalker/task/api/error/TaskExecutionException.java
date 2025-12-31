package io.lonmstalker.task.api.error;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Execution error.
 */
public final class TaskExecutionException extends TaskException {

    public TaskExecutionException(
        @NonNull String message
    ) {
        super(Objects.requireNonNull(message, "message"));
    }

    public TaskExecutionException(
        @NonNull String message,
        @NonNull Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    }
}
