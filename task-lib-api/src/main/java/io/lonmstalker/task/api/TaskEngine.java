package io.lonmstalker.task.api;

import io.lonmstalker.task.api.annotations.Blocking;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskRequest;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.model.TaskSubmissionResult;
import net.jcip.annotations.ThreadSafe;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Task execution engine.
 */
@ThreadSafe
public interface TaskEngine extends AutoCloseable {

    /**
     * Starts background processing.
     *
     * @throws io.lonmstalker.task.api.error.TaskException on start failures
     */
    void start();

    /**
     * Submits a task with idempotency handling.
     *
     * @param request task request
     * @return submission result
     */
    @Blocking
    @NonNull TaskSubmissionResult submit(
        @NonNull TaskRequest<?> request
    );

    /**
     * Finds a task by idempotency key.
     *
     * @param key task key
     * @return snapshot or null
     */
    @Blocking
    @Nullable TaskSnapshot findByKey(
        @NonNull TaskKey key
    );

    /**
     * Finds a task by identifier.
     *
     * @param id task id
     * @return snapshot or null
     */
    @Blocking
    @Nullable TaskSnapshot findById(
        @NonNull TaskId id
    );

    @Override
    @Blocking
    void close();
}
