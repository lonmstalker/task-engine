package io.lonmstalker.task.api.error;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Invalid state transition.
 */
public final class TaskStateException extends TaskException {

    public TaskStateException(
        @NonNull String message
    ) {
        super(Objects.requireNonNull(message, "message"));
    }

    public TaskStateException(
        @NonNull String message,
        @NonNull Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    }
}
