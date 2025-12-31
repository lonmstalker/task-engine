package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStats;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStatsProvider;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

final class KafkaTaskOutboxReporter implements SmartInitializingSingleton {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(5);

    private final TaskEventOutboxStore outboxStore;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    KafkaTaskOutboxReporter(
        TaskEventOutboxStore outboxStore,
        ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.outboxStore = Objects.requireNonNull(outboxStore, "outboxStore");
        this.meterRegistryProvider = Objects.requireNonNull(meterRegistryProvider, "meterRegistryProvider");
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!(outboxStore instanceof TaskEventOutboxStatsProvider provider)) {
            return;
        }

        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }

        StatsCache cache = new StatsCache(provider, DEFAULT_TTL);

        Gauge.builder("task.kafka.outbox.pending", cache, c -> c.get().pending())
            .description("Number of pending Kafka outbox events")
            .register(registry);
        Gauge.builder("task.kafka.outbox.dead_lettered", cache, c -> c.get().deadLettered())
            .description("Number of dead-lettered Kafka outbox events")
            .register(registry);
    }

    private static final class StatsCache {
        private final TaskEventOutboxStatsProvider provider;
        private final long ttlNanos;
        private final AtomicReference<TaskEventOutboxStats> current = new AtomicReference<>(new TaskEventOutboxStats(0, 0));
        private final AtomicLong lastUpdated = new AtomicLong(0L);

        private StatsCache(TaskEventOutboxStatsProvider provider, Duration ttl) {
            this.provider = provider;
            this.ttlNanos = ttl.toNanos();
        }

        TaskEventOutboxStats get() {
            long now = System.nanoTime();
            long last = lastUpdated.get();
            if (now - last > ttlNanos) {
                try {
                    TaskEventOutboxStats stats = provider.loadStats();
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
