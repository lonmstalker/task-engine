package io.lonmstalker.task.api.model;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Link to another task.
 */
public record TaskLink(
    @NonNull TaskId targetId,
    @NonNull TaskLinkType type
) {

    public TaskLink {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(type, "type");
    }
}
