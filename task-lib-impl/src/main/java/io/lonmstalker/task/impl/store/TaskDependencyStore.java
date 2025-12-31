package io.lonmstalker.task.impl.store;

import io.lonmstalker.task.api.annotations.Blocking;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskErrorInfo;
import java.time.Instant;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Optional extension for stores that can resolve and update task dependencies.
 */
public interface TaskDependencyStore {

    @Blocking
    @NonNull List<TaskId> findFailedDependencies(
        @NonNull List<TaskLink> links
    );

    @Blocking
    void cancelDependents(
        @NonNull TaskId rootTaskId,
        @NonNull TaskErrorInfo error,
        @NonNull Instant now
    );

    @Blocking
    void cancelBlockedByFailedDependencies(
        @NonNull TaskErrorInfo error,
        @NonNull Instant now
    );
}
