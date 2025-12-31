package io.lonmstalker.task.kafka.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Claim settings for outbox polling.
 */
public record TaskEventOutboxClaim(
    @NonNull Instant now,
    @NonNull Duration leaseDuration,
    int maxEvents,
    @NonNull String leaseOwner
) {

    public TaskEventOutboxClaim {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        Objects.requireNonNull(leaseOwner, "leaseOwner");
        if (leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be >= 0");
        }
        if (maxEvents <= 0) {
            throw new IllegalArgumentException("maxEvents must be > 0");
        }
        if (leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner must not be blank");
        }
    }
}
