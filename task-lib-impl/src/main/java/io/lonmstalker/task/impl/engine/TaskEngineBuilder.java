package io.lonmstalker.task.impl.engine;

import com.google.common.base.Preconditions;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskDispatcher;
import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.event.TaskEventStore;
import io.lonmstalker.task.api.store.TaskStore;
import io.lonmstalker.task.impl.dispatcher.ExecutorTaskDispatcher;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import io.lonmstalker.task.api.model.TaskType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Builder for task engine.
 */
public final class TaskEngineBuilder {

    private static final @NonNull Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final @NonNull Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(30);
    private static final @NonNull Duration DEFAULT_RECOVERY_INTERVAL = Duration.ofSeconds(10);
    private static final int DEFAULT_CLAIM_BATCH_SIZE = 100;

    private @Nullable TaskStore store;
    private @Nullable TaskEventStore eventStore;
    private @Nullable TaskDispatcher dispatcher;
    private @NonNull Clock clock = Clock.systemUTC();
    private @NonNull Duration pollInterval = DEFAULT_POLL_INTERVAL;
    private @NonNull Duration leaseDuration = DEFAULT_LEASE_DURATION;
    private @NonNull Duration recoveryInterval = DEFAULT_RECOVERY_INTERVAL;
    private int claimBatchSize = DEFAULT_CLAIM_BATCH_SIZE;
    private @NonNull String engineId = "engine-" + UUID.randomUUID();
    private final @NonNull Map<TaskType, TaskDefinition<?>> definitions = new HashMap<>();

    public static @NonNull TaskEngineBuilder builder() {
        return new TaskEngineBuilder();
    }

    private TaskEngineBuilder() {
    }

    public @NonNull TaskEngineBuilder store(
        @NonNull TaskStore store
    ) {
        this.store = Objects.requireNonNull(store, "store");
        return this;
    }

    public @NonNull TaskEngineBuilder dispatcher(
        @NonNull TaskDispatcher dispatcher
    ) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        return this;
    }

    public @NonNull TaskEngineBuilder eventStore(
        @NonNull TaskEventStore eventStore
    ) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        return this;
    }

    public @NonNull TaskEngineBuilder clock(
        @NonNull Clock clock
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        return this;
    }

    public @NonNull TaskEngineBuilder pollInterval(
        @NonNull Duration pollInterval
    ) {
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
        Preconditions.checkArgument(!pollInterval.isNegative(), "pollInterval must be >= 0");
        return this;
    }

    public @NonNull TaskEngineBuilder leaseDuration(
        @NonNull Duration leaseDuration
    ) {
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        Preconditions.checkArgument(!leaseDuration.isNegative(), "leaseDuration must be >= 0");
        return this;
    }

    public @NonNull TaskEngineBuilder recoveryInterval(
        @NonNull Duration recoveryInterval
    ) {
        this.recoveryInterval = Objects.requireNonNull(recoveryInterval, "recoveryInterval");
        Preconditions.checkArgument(!recoveryInterval.isNegative(), "recoveryInterval must be >= 0");
        return this;
    }

    public @NonNull TaskEngineBuilder claimBatchSize(
        int claimBatchSize
    ) {
        Preconditions.checkArgument(claimBatchSize > 0, "claimBatchSize must be > 0");
        this.claimBatchSize = claimBatchSize;
        return this;
    }

    public @NonNull TaskEngineBuilder engineId(
        @NonNull String engineId
    ) {
        Objects.requireNonNull(engineId, "engineId");
        Preconditions.checkArgument(!engineId.isBlank(), "engineId must not be blank");
        this.engineId = engineId;
        return this;
    }

    public @NonNull TaskEngineBuilder registerDefinition(
        @NonNull TaskDefinition<?> definition
    ) {
        Objects.requireNonNull(definition, "definition");
        definitions.put(definition.type(), definition);
        return this;
    }

    public @NonNull TaskEngine build() {
        Objects.requireNonNull(store, "store");
        if (dispatcher == null) {
            dispatcher = ExecutorTaskDispatcher.fixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                "task-worker-%d"
            );
        }

        TaskEventStore resolvedEventStore = eventStore;
        if (resolvedEventStore == null && store instanceof TaskEventStore) {
            resolvedEventStore = (TaskEventStore) store;
        }

        Map<TaskType, TaskDefinition<?>> registry = Map.copyOf(definitions);
        TaskEventPublisher eventPublisher = TaskEventPublisherFactory.create(store, resolvedEventStore, registry);

        return new TaskEngineImpl(
            store,
            dispatcher,
            clock,
            pollInterval,
            leaseDuration,
            recoveryInterval,
            claimBatchSize,
            engineId,
            registry,
            eventPublisher
        );
    }
}
