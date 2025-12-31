package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.api.store.TaskStore;
import io.lonmstalker.task.api.store.TaskStoreStats;
import io.lonmstalker.task.api.store.TaskStoreStatsProvider;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

final class TaskStoreReporter implements SmartInitializingSingleton {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(5);

    private final TaskStore store;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    TaskStoreReporter(
        TaskStore store,
        ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.meterRegistryProvider = Objects.requireNonNull(meterRegistryProvider, "meterRegistryProvider");
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!(store instanceof TaskStoreStatsProvider provider)) {
            return;
        }

        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }

        StatsCache cache = new StatsCache(provider, DEFAULT_TTL);

        Gauge.builder("task.store.total", cache, c -> c.get().total())
            .description("Total number of tasks")
            .register(registry);
        Gauge.builder("task.store.pending", cache, c -> c.get().pending())
            .description("Number of pending tasks")
            .register(registry);
        Gauge.builder("task.store.running", cache, c -> c.get().running())
            .description("Number of running tasks")
            .register(registry);
        Gauge.builder("task.store.waiting_retry", cache, c -> c.get().waitingRetry())
            .description("Number of tasks waiting for retry")
            .register(registry);
        Gauge.builder("task.store.completed", cache, c -> c.get().completed())
            .description("Number of completed tasks")
            .register(registry);
        Gauge.builder("task.store.failed", cache, c -> c.get().failed())
            .description("Number of failed tasks")
            .register(registry);
        Gauge.builder("task.store.cancelled", cache, c -> c.get().cancelled())
            .description("Number of cancelled tasks")
            .register(registry);
    }

    private static final class StatsCache {
        private final TaskStoreStatsProvider provider;
        private final long ttlNanos;
        private final AtomicReference<TaskStoreStats> current = new AtomicReference<>(new TaskStoreStats(0, 0, 0, 0, 0, 0, 0));
        private final AtomicLong lastUpdated = new AtomicLong(0L);

        private StatsCache(TaskStoreStatsProvider provider, Duration ttl) {
            this.provider = provider;
            this.ttlNanos = ttl.toNanos();
        }

        TaskStoreStats get() {
            long now = System.nanoTime();
            long last = lastUpdated.get();
            if (now - last > ttlNanos) {
                try {
                    TaskStoreStats stats = provider.loadStats();
                    current.set(stats);
                    lastUpdated.set(now);
                } catch (RuntimeException e) {
                    lastUpdated.set(now);
                }
            }
            return current.get();
        }
    }
}
