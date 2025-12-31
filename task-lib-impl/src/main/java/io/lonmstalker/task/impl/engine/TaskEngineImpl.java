package io.lonmstalker.task.impl.engine;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskDispatcher;
import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.error.TaskConfigException;
import io.lonmstalker.task.api.model.TaskErrorInfo;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskRequest;
import io.lonmstalker.task.api.model.TaskSubmissionResult;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.store.TaskClaim;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskStore;
import io.lonmstalker.task.impl.store.TaskDependencyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.jcip.annotations.ThreadSafe;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default task engine implementation.
 */
@ThreadSafe
public final class TaskEngineImpl implements TaskEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskEngineImpl.class);

    private final @NonNull TaskStore store;
    private final @NonNull TaskDispatcher dispatcher;
    private final @NonNull Clock clock;
    private final @NonNull Duration pollInterval;
    private final @NonNull Duration leaseDuration;
    private final @NonNull Duration recoveryInterval;
    private final int claimBatchSize;
    private final @NonNull String engineId;
    private final @NonNull Map<TaskType, TaskDefinition<?>> definitions;
    private final @NonNull ScheduledExecutorService scheduler;
    private final @NonNull TaskEventPublisher eventPublisher;
    private final @NonNull TaskSnapshotMapper snapshotMapper;
    private final @NonNull TaskSubmissionService submissionService;
    private final @NonNull TaskProcessingService processingService;
    private final @NonNull AtomicBoolean started = new AtomicBoolean(false);
    private final @NonNull AtomicBoolean closed = new AtomicBoolean(false);

    TaskEngineImpl(
        @NonNull TaskStore store,
        @NonNull TaskDispatcher dispatcher,
        @NonNull Clock clock,
        @NonNull Duration pollInterval,
        @NonNull Duration leaseDuration,
        @NonNull Duration recoveryInterval,
        int claimBatchSize,
        @NonNull String engineId,
        @NonNull Map<TaskType, TaskDefinition<?>> definitions,
        @NonNull TaskEventPublisher eventPublisher
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        this.recoveryInterval = Objects.requireNonNull(recoveryInterval, "recoveryInterval");
        Preconditions.checkArgument(claimBatchSize > 0, "claimBatchSize must be > 0");
        this.claimBatchSize = claimBatchSize;
        this.engineId = Objects.requireNonNull(engineId, "engineId");
        this.definitions = Map.copyOf(definitions);
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.snapshotMapper = new TaskSnapshotMapper(store);
        this.submissionService = new TaskSubmissionService(store, eventPublisher, snapshotMapper, clock);
        this.processingService = new TaskProcessingService(store, eventPublisher, snapshotMapper, clock);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat("task-engine-scheduler-%d")
                .setDaemon(true)
                .build()
        );
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        scheduler.scheduleAtFixedRate(
            () -> safeRun("poll", this::pollOnce),
            0,
            pollInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );

        scheduler.scheduleAtFixedRate(
            () -> safeRun("recovery", this::recoverExpiredLeases),
            recoveryInterval.toMillis(),
            recoveryInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    @Override
    public @NonNull TaskSubmissionResult submit(
        @NonNull TaskRequest<?> request
    ) {
        Objects.requireNonNull(request, "request");

        TaskDefinition<?> definition = definitionFor(request.type());

        return submissionService.submit(request, definition);
    }

    @Override
    public @Nullable TaskSnapshot findByKey(
        @NonNull TaskKey key
    ) {
        Objects.requireNonNull(key, "key");

        TaskRecord record = store.findByKey(key);
        if (record == null) {
            return null;
        }

        return snapshotMapper.toSnapshot(record);
    }

    @Override
    public @Nullable TaskSnapshot findById(
        @NonNull TaskId id
    ) {
        Objects.requireNonNull(id, "id");

        TaskRecord record = store.findById(id);
        if (record == null) {
            return null;
        }

        return snapshotMapper.toSnapshot(record);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }

        dispatcher.close();
        eventPublisher.close();
        store.close();
    }

    // ───────────────────────────────────────────────────
    // Scheduler
    // ───────────────────────────────────────────────────

    void pollOnce() {
        if (closed.get()) {
            return;
        }

        TaskClaim claim = new TaskClaim(
            claimBatchSize,
            engineId,
            leaseDuration,
            now()
        );

        List<TaskRecord> claimed = store.claim(claim);
        if (claimed.isEmpty()) {
            return;
        }

        for (TaskRecord record : claimed) {
            TaskDefinition<?> definition = definitionFor(record.type());
            dispatcher.dispatch(() -> safeProcess(record, definition));
        }
    }

    void recoverExpiredLeases() {
        if (closed.get()) {
            return;
        }

        Instant now = now();
        store.resetExpiredLeases(now);
        if (store instanceof TaskDependencyStore dependencyStore) {
            dependencyStore.cancelBlockedByFailedDependencies(
                new TaskErrorInfo("DependencyFailed", "Dependency failed"),
                now
            );
        }
    }

    // ───────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────

    private TaskDefinition<?> definitionFor(
        @NonNull TaskType type
    ) {
        Objects.requireNonNull(type, "type");

        TaskDefinition<?> definition = definitions.get(type);
        if (definition == null) {
            throw new TaskConfigException("Task type not registered: " + type.value());
        }

        return definition;
    }

    private @NonNull Instant now() {
        return Instant.now(clock);
    }

    private void safeRun(
        @NonNull String name,
        @NonNull Runnable action
    ) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOGGER.error("Task engine {} loop failed", name, e);
        }
    }

    private void safeProcess(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<?> definition
    ) {
        try {
            processingService.process(record, definition);
        } catch (RuntimeException e) {
            LOGGER.error("Task processing failed for {}", record.id().value(), e);
        }
    }
}
