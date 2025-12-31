package io.lonmstalker.task.api;

import io.lonmstalker.task.api.annotations.Blocking;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Dispatches task execution to worker threads or external workers.
 */
public interface TaskDispatcher extends AutoCloseable {

    /**
     * Dispatches task execution.
     *
     * @param task runnable task
     */
    void dispatch(
        @NonNull Runnable task
    );

    /**
     * Returns maximum parallelism.
     *
     * @return parallelism
     */
    int parallelism();

    @Override
    @Blocking
    void close();
}
