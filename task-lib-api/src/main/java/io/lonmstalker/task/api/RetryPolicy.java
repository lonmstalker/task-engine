package io.lonmstalker.task.api;

import io.lonmstalker.task.api.error.TaskException;
import java.time.Duration;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Retry policy for failed task executions.
 */
public interface RetryPolicy {

    /**
     * Returns max execution attempts.
     *
     * @return max attempts
     */
    int maxAttempts();

    /**
     * Returns delay before next retry.
     *
     * @param attempt current attempt
     * @return delay duration
     */
    @NonNull Duration delayBetweenAttempts(
        int attempt
    );

    /**
     * Decides whether retry should happen.
     *
     * @param error failure error
     * @return true if retry should happen
     */
    boolean shouldRetry(
        @NonNull TaskException error
    );
}
