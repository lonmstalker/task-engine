package io.lonmstalker.task.api;

import io.lonmstalker.task.api.error.TaskException;
import java.time.Duration;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Retry policy factories.
 */
public final class RetryPolicies {

    private static final @NonNull RetryPolicy NONE = new NoRetryPolicy();

    private RetryPolicies() {
    }

    /**
     * Returns retry policy that never retries.
     *
     * @return retry policy
     */
    public static @NonNull RetryPolicy none() {
        return NONE;
    }

    /**
     * Returns retry policy with fixed delay.
     *
     * @param maxAttempts max attempts
     * @param delay delay between attempts
     * @return retry policy
     */
    public static @NonNull RetryPolicy fixedDelay(
        int maxAttempts,
        @NonNull Duration delay
    ) {
        return new FixedDelayRetryPolicy(maxAttempts, delay);
    }

    private static final class NoRetryPolicy implements RetryPolicy {

        @Override
        public int maxAttempts() {
            return 1;
        }

        @Override
        public @NonNull Duration delayBetweenAttempts(
            int attempt
        ) {
            return Duration.ZERO;
        }

        @Override
        public boolean shouldRetry(
            @NonNull TaskException error
        ) {
            Objects.requireNonNull(error, "error");
            return false;
        }
    }

    private static final class FixedDelayRetryPolicy implements RetryPolicy {
        private final int maxAttempts;
        private final @NonNull Duration delay;

        private FixedDelayRetryPolicy(
            int maxAttempts,
            @NonNull Duration delay
        ) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }
            this.maxAttempts = maxAttempts;
            this.delay = Objects.requireNonNull(delay, "delay");
        }

        @Override
        public int maxAttempts() {
            return maxAttempts;
        }

        @Override
        public @NonNull Duration delayBetweenAttempts(
            int attempt
        ) {
            return delay;
        }

        @Override
        public boolean shouldRetry(
            @NonNull TaskException error
        ) {
            Objects.requireNonNull(error, "error");
            return true;
        }
    }
}
