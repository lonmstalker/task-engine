package io.lonmstalker.task.api.model;

import io.lonmstalker.task.api.error.TaskException;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Result of task execution.
 */
public sealed interface TaskResult permits TaskResult.Success, TaskResult.Failure {

    /**
     * Creates successful result.
     *
     * @return success result
     */
    static @NonNull Success success() {
        return new Success(null);
    }

    /**
     * Creates successful result with optional next state.
     *
     * @param nextState next state override
     * @return success result
     */
    static @NonNull Success success(
        @Nullable TaskState nextState
    ) {
        return new Success(nextState);
    }

    /**
     * Creates failure result.
     *
     * @param error failure error
     * @return failure result
     */
    static @NonNull Failure failure(
        @NonNull TaskException error
    ) {
        return new Failure(error);
    }

    /**
     * Successful execution.
     */
    record Success(
        @Nullable TaskState nextState
    ) implements TaskResult {
    }

    /**
     * Failed execution.
     */
    record Failure(
        @NonNull TaskException error
    ) implements TaskResult {

        public Failure {
            Objects.requireNonNull(error, "error");
        }
    }
}
