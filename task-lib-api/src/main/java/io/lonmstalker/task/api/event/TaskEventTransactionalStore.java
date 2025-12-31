package io.lonmstalker.task.api.event;

import io.lonmstalker.task.api.annotations.Blocking;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Event store with transaction support.
 */
public interface TaskEventTransactionalStore extends TaskEventStore {

    /**
     * Runs actions inside a single transaction.
     *
     * @param action action to execute
     * @return action result
     * @param <T> result type
     */
    @Blocking
    <T> T inTransaction(
        @NonNull Supplier<T> action
    );
}
