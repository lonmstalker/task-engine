package io.lonmstalker.task.api.error;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Base exception for task failures.
 */
public class TaskException extends RuntimeException {

    public TaskException(
        @NonNull String message
    ) {
        super(Objects.requireNonNull(message, "message"));
    }

    public TaskException(
        @NonNull String message,
        @NonNull Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    }
}
