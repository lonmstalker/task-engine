package io.lonmstalker.task.api.model;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Execution context for a task handler.
 */
public record TaskExecutionContext<C>(
    @NonNull TaskSnapshot snapshot,
    @NonNull C context
) {

    public TaskExecutionContext {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(context, "context");
    }
}
