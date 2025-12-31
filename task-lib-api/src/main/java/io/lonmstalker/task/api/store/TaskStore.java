package io.lonmstalker.task.api.store;

import io.lonmstalker.task.api.annotations.Blocking;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskLink;
import java.time.Instant;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Persistent store for tasks.
 */
public interface TaskStore extends AutoCloseable {

    /**
     * Creates a new task.
     *
     * @param record task record
     * @param links task links
     * @return stored record
     */
    @Blocking
    @NonNull TaskRecord create(
        @NonNull TaskRecord record,
        @NonNull List<TaskLink> links
    );

    /**
     * Updates existing task by key using transactional updater.
     *
     * @param key idempotency key
     * @param updater record updater
     * @param links task links
     * @return updated record
     */
    @Blocking
    @NonNull TaskRecord updateOnDuplicate(
        @NonNull TaskKey key,
        @NonNull TaskRecordUpdater updater,
        @NonNull List<TaskLink> links
    );

    /**
     * Finds task by idempotency key.
     *
     * @param key task key
     * @return task record or null
     */
    @Blocking
    @Nullable TaskRecord findByKey(
        @NonNull TaskKey key
    );

    /**
     * Finds task by id.
     *
     * @param id task id
     * @return task record or null
     */
    @Blocking
    @Nullable TaskRecord findById(
        @NonNull TaskId id
    );

    /**
     * Claims tasks for execution.
     *
     * @param claim claim request
     * @return claimed tasks
     */
    @Blocking
    @NonNull List<TaskRecord> claim(
        @NonNull TaskClaim claim
    );

    /**
     * Updates task record.
     *
     * @param record updated record
     * @return updated record
     */
    @Blocking
    @NonNull TaskRecord update(
        @NonNull TaskRecord record
    );

    /**
     * Loads task links.
     *
     * @param id task id
     * @return task links
     */
    @Blocking
    @NonNull List<TaskLink> findLinks(
        @NonNull TaskId id
    );

    /**
     * Resets expired leases to make tasks runnable again.
     *
     * @param now current time
     */
    @Blocking
    void resetExpiredLeases(
        @NonNull Instant now
    );

    @Override
    void close();
}
