package io.lonmstalker.task.api.error;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Duplicate task submission detected.
 */
public final class TaskDuplicateException extends TaskValidationException {

    public TaskDuplicateException(
        @NonNull String message
    ) {
        super(Objects.requireNonNull(message, "message"));
    }

    public TaskDuplicateException(
        @NonNull String message,
        @NonNull Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    }
}
