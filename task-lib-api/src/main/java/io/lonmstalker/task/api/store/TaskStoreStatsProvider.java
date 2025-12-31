package io.lonmstalker.task.api.store;

import io.lonmstalker.task.api.annotations.Blocking;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Optional extension for stores that can provide task counts for metrics.
 */
public interface TaskStoreStatsProvider {

    @Blocking
    @NonNull TaskStoreStats loadStats();
}
