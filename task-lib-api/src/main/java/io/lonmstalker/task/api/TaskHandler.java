package io.lonmstalker.task.api;

import io.lonmstalker.task.api.model.TaskExecutionContext;
import io.lonmstalker.task.api.model.TaskResult;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Handler for task execution.
 */
@FunctionalInterface
public interface TaskHandler<C> {

    /**
     * Executes task logic.
     *
     * @param context execution context
     * @return execution result
     */
    @NonNull TaskResult handle(
        @NonNull TaskExecutionContext<C> context
    );
}
