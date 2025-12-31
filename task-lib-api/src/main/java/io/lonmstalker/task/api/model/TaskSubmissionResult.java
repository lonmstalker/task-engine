package io.lonmstalker.task.api.model;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Result of task submission.
 */
public record TaskSubmissionResult(
    @NonNull TaskSnapshot snapshot,
    @NonNull TaskSubmissionStatus status
) {

    public TaskSubmissionResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(status, "status");
    }
}
