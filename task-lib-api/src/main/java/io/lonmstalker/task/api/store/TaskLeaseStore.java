package io.lonmstalker.task.api.store;

import io.lonmstalker.task.api.annotations.Blocking;
import java.time.Instant;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Optional extension for stores that can enforce lease ownership on updates.
 */
public interface TaskLeaseStore {

    /**
     * Updates a task only if the lease matches the expected owner and expiry.
     *
     * @param record updated record
     * @param expectedLeaseOwner expected lease owner
     * @param expectedLeaseUntil expected lease expiry
     * @param now current time for expiry check
     * @return true if the update succeeded
     */
    @Blocking
    boolean updateIfLeased(
        @NonNull TaskRecord record,
        @NonNull String expectedLeaseOwner,
        @NonNull Instant expectedLeaseUntil,
        @NonNull Instant now
    );
}
