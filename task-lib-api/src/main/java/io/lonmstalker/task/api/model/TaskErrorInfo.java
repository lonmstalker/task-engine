package io.lonmstalker.task.api.model;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Error details stored for diagnostics.
 */
public record TaskErrorInfo(
    @NonNull String type,
    @NonNull String message
) {

    public TaskErrorInfo {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(message, "message");
    }
}
