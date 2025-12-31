package io.lonmstalker.task.api.store;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Updates a task record inside store transaction.
 */
@FunctionalInterface
public interface TaskRecordUpdater {

    /**
     * Returns updated record.
     *
     * @param existing existing record
     * @return updated record
     */
    @NonNull TaskRecord update(
        @NonNull TaskRecord existing
    );
}
