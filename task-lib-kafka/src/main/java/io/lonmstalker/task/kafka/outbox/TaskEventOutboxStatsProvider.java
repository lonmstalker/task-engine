package io.lonmstalker.task.kafka.outbox;

import io.lonmstalker.task.api.annotations.Blocking;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Optional extension for outbox stores that can provide stats for metrics.
 */
public interface TaskEventOutboxStatsProvider {

    @Blocking
    @NonNull TaskEventOutboxStats loadStats();
}
