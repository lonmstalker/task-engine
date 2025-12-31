package io.lonmstalker.task.api.store;

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Claim request for tasks.
 */
public record TaskClaim(
    int maxTasks,
    @NonNull String leaseOwner,
    @NonNull Duration leaseDuration,
    @NonNull Instant now
) {

    public TaskClaim {
        Preconditions.checkArgument(maxTasks > 0, "maxTasks must be > 0");
        Objects.requireNonNull(leaseOwner, "leaseOwner");
        Preconditions.checkArgument(!leaseOwner.isBlank(), "leaseOwner must not be blank");
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        Objects.requireNonNull(now, "now");
    }
}
