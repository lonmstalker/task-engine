package io.lonmstalker.task.impl.engine;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.lonmstalker.task.api.TaskContextCodec;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskDispatcher;
import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventContextKind;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.event.TaskEventStore;
import io.lonmstalker.task.api.event.TaskEventTransactionalStore;
import io.lonmstalker.task.api.event.TaskEventType;
import io.lonmstalker.task.api.error.TaskConfigException;
import io.lonmstalker.task.api.error.TaskDuplicateException;
import io.lonmstalker.task.api.error.TaskExecutionException;
import io.lonmstalker.task.api.error.TaskException;
import io.lonmstalker.task.api.error.TaskStateException;
import io.lonmstalker.task.api.model.TaskErrorInfo;
import io.lonmstalker.task.api.model.TaskExecutionContext;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskLinkType;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskRequest;
import io.lonmstalker.task.api.model.TaskResult;
import io.lonmstalker.task.api.model.TaskResult.Success;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskSubmissionResult;
import io.lonmstalker.task.api.model.TaskSubmissionStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.state.TaskStateUpdate;
import io.lonmstalker.task.api.state.TaskStateUpdateAction;
import io.lonmstalker.task.api.store.TaskClaim;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskRecordUpdater;
import io.lonmstalker.task.api.store.TaskStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import net.jcip.annotations.ThreadSafe;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Default task engine implementation.
 */
@Slf4j
@ThreadSafe
public final class TaskEngineImpl implements TaskEngine {

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
    private final @Nullable TaskEventStore eventStore;
    private final @Nullable TaskEventTransactionalStore transactionalEventStore;
    private final boolean sharedEventStore;
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
        @Nullable TaskEventStore eventStore
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
        this.eventStore = eventStore;
        this.transactionalEventStore = eventStore instanceof TaskEventTransactionalStore
            ? (TaskEventTransactionalStore) eventStore
            : null;
        this.sharedEventStore = eventStore != null
            && eventStore == store
            && eventStore instanceof TaskEventTransactionalStore;
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
            this::pollOnce,
            0,
            pollInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );

        scheduler.scheduleAtFixedRate(
            this::recoverExpiredLeases,
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

        TaskDefinition<?> rawDefinition = definitionFor(request.type());

        return submitInternal(request, rawDefinition);
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

        return toSnapshot(record, loadLinks(record.id()));
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

        return toSnapshot(record, loadLinks(record.id()));
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
            dispatcher.dispatch(() -> processTask(record));
        }
    }

    void recoverExpiredLeases() {
        if (closed.get()) {
            return;
        }

        store.resetExpiredLeases(now());
    }

    // ───────────────────────────────────────────────────
    // Processing
    // ───────────────────────────────────────────────────

    private void processTask(
        @NonNull TaskRecord record
    ) {
        Objects.requireNonNull(record, "record");

        TaskDefinition<?> rawDefinition = definitionFor(record.type());

        processTyped(record, rawDefinition);
    }

    private <C> void processTyped(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<C> definition
    ) {
        if (definition.stateMachine().isTerminal(record.state())) {
            TaskRecord completed = new TaskRecord(
                record.id(),
                record.key(),
                record.type(),
                record.state(),
                TaskStatus.COMPLETED,
                record.attempt(),
                record.maxAttempts(),
                null,
                null,
                null,
                record.payload(),
                record.createdAt(),
                now(),
                null
            );

            completeWithEvent(completed);
            return;
        }

        TaskPayload payload = record.payload();
        TaskContextCodec<C> codec = definition.contextCodec();
        C context;

        try {
            context = codec.decode(payload);
        } catch (RuntimeException e) {
            log.error("Failed to decode task context for {}", record.id().value(), e);
            handleFailure(record, definition, new TaskExecutionException("Context decoding failed", e));
            return;
        }

        TaskSnapshot snapshot = toSnapshot(record, loadLinks(record.id()));
        TaskExecutionContext<C> executionContext = new TaskExecutionContext<>(snapshot, context);

        TaskResult result;
        try {
            result = definition.handler().handle(executionContext);
        } catch (TaskException e) {
            result = TaskResult.failure(e);
        } catch (RuntimeException e) {
            result = TaskResult.failure(new TaskExecutionException("Task handler failed", e));
        }
        if (result == null) {
            handleFailure(record, definition, new TaskExecutionException("Task handler returned null"));
            return;
        }

        if (result instanceof TaskResult.Success success) {
            handleSuccess(record, definition, success);
            return;
        }

        if (result instanceof TaskResult.Failure failure) {
            handleFailure(record, definition, failure.error());
        }
    }

    private <C> void handleSuccess(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<C> definition,
        @NonNull Success success
    ) {
        TaskState nextState = resolveNextState(record, definition, success.nextState());
        Instant now = now();

        if (definition.stateMachine().isTerminal(nextState)) {
            TaskRecord completed = new TaskRecord(
                record.id(),
                record.key(),
                record.type(),
                nextState,
                TaskStatus.COMPLETED,
                record.attempt(),
                record.maxAttempts(),
                null,
                null,
                null,
                record.payload(),
                record.createdAt(),
                now,
                null
            );

            completeWithEvent(completed);
            return;
        }

        TaskRecord updated = new TaskRecord(
            record.id(),
            record.key(),
            record.type(),
            nextState,
            TaskStatus.PENDING,
            record.attempt(),
            record.maxAttempts(),
            now,
            null,
            null,
            record.payload(),
            record.createdAt(),
            now,
            null
        );

        store.update(updated);
    }

    private <C> void handleFailure(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<C> definition,
        @NonNull TaskException error
    ) {
        TaskErrorInfo errorInfo = toErrorInfo(error);
        Instant now = now();

        boolean shouldRetry = definition.retryPolicy().shouldRetry(error)
            && record.attempt() < record.maxAttempts();

        if (shouldRetry) {
            Duration delay = definition.retryPolicy().delayBetweenAttempts(record.attempt());
            Instant nextRunAt = now.plus(delay);

            TaskRecord rescheduled = new TaskRecord(
                record.id(),
                record.key(),
                record.type(),
                record.state(),
                TaskStatus.WAITING_RETRY,
                record.attempt(),
                record.maxAttempts(),
                nextRunAt,
                null,
                null,
                record.payload(),
                record.createdAt(),
                now,
                errorInfo
            );

            store.update(rescheduled);
            return;
        }

        TaskRecord failed = new TaskRecord(
            record.id(),
            record.key(),
            record.type(),
            record.state(),
            TaskStatus.FAILED,
            record.attempt(),
            record.maxAttempts(),
            null,
            null,
            null,
            record.payload(),
            record.createdAt(),
            now,
            errorInfo
        );

        store.update(failed);
    }

    private <C> TaskState resolveNextState(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<C> definition,
        @Nullable TaskState override
    ) {
        if (override == null) {
            return definition.stateMachine().nextState(record.state());
        }

        if (!definition.stateMachine().isValidTransition(record.state(), override)) {
            throw new TaskStateException(
                "Invalid transition from " + record.state().value() + " to " + override.value()
            );
        }

        return override;
    }

    // ───────────────────────────────────────────────────
    // Submission
    // ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <C> TaskSubmissionResult submitInternal(
        @NonNull TaskRequest<?> rawRequest,
        @NonNull TaskDefinition<?> rawDefinition
    ) {
        TaskRequest<C> request = (TaskRequest<C>) rawRequest;
        TaskDefinition<C> definition = (TaskDefinition<C>) rawDefinition;

        Instant now = now();
        TaskState initial = definition.stateMachine().initialState();
        TaskState resolvedState = resolveInitialState(definition, initial, request.state());
        TaskPayload payload = definition.contextCodec().encode(request.context());

        TaskRecord record = new TaskRecord(
            TaskId.random(),
            request.key(),
            request.type(),
            resolvedState,
            TaskStatus.PENDING,
            0,
            definition.retryPolicy().maxAttempts(),
            now,
            null,
            null,
            payload,
            now,
            now,
            null
        );

        try {
            return withSharedTransaction(() -> {
                TaskRecord created = store.create(record, request.links());
                recordRequestContext(created, payload, now);
                TaskSnapshot snapshot = toSnapshot(created, loadLinks(created.id()));

                return new TaskSubmissionResult(snapshot, TaskSubmissionStatus.CREATED);
            });
        } catch (TaskDuplicateException ex) {
            return updateDuplicate(request, definition);
        }
    }

    private <C> TaskSubmissionResult updateDuplicate(
        @NonNull TaskRequest<C> request,
        @NonNull TaskDefinition<C> definition
    ) {
        if (sharedEventStore && transactionalEventStore != null) {
            return transactionalEventStore.inTransaction(() -> updateDuplicateInternal(request, definition));
        }

        return updateDuplicateInternal(request, definition);
    }

    private <C> TaskSubmissionResult updateDuplicateInternal(
        @NonNull TaskRequest<C> request,
        @NonNull TaskDefinition<C> definition
    ) {
        Instant now = now();
        TaskRecordUpdater updater = existing -> mergeDuplicate(existing, request, definition, now);
        UpdateTracker tracker = new UpdateTracker();

        TaskRecord updated = store.updateOnDuplicate(request.key(), record -> {
            TaskRecord merged = updater.update(record);
            tracker.changed = tracker.changed || isChanged(record, merged) || !request.links().isEmpty();
            return merged;
        }, request.links());

        TaskSnapshot snapshot = toSnapshot(updated, loadLinks(updated.id()));
        TaskSubmissionStatus status = tracker.changed ? TaskSubmissionStatus.UPDATED : TaskSubmissionStatus.DUPLICATE;

        TaskPayload duplicatePayload = null;
        if (eventStore != null) {
            duplicatePayload = definition.contextCodec().encode(request.context());
        }

        if (eventStore != null && duplicatePayload != null) {
            TaskPayload payload = duplicatePayload;
            Runnable eventOps = () -> {
                recordDuplicateContext(updated, payload, now);
                if (shouldEmitLateEvent(updated, definition)) {
                    emitCompletionEvent(updated, now);
                }
            };

            if (sharedEventStore) {
                eventOps.run();
            } else {
                runEventTransaction(eventOps);
            }
        }

        return new TaskSubmissionResult(snapshot, status);
    }

    private <C> TaskRecord mergeDuplicate(
        @NonNull TaskRecord existing,
        @NonNull TaskRequest<C> request,
        @NonNull TaskDefinition<C> definition,
        @NonNull Instant now
    ) {
        TaskStateUpdate update = definition.stateMachine().resolveExternalState(existing.state(), request.state());
        TaskState newState = existing.state();

        if (update.action() == TaskStateUpdateAction.APPLY) {
            newState = Optional.ofNullable(update.state())
                .orElseThrow(() -> new TaskStateException("Missing state for update"));
        } else if (update.action() == TaskStateUpdateAction.REJECT) {
            String reason = Optional.ofNullable(update.reason()).orElse("Rejected state update");
            throw new TaskStateException(reason);
        }

        boolean canMergeContext = existing.status() == TaskStatus.PENDING
            || existing.status() == TaskStatus.WAITING_RETRY;

        TaskPayload newPayload = existing.payload();
        if (canMergeContext) {
            TaskContextCodec<C> codec = definition.contextCodec();
            C existingContext = codec.decode(existing.payload());
            C merged = definition.contextMerger().merge(existingContext, request.context());
            newPayload = codec.encode(merged);
        }

        return new TaskRecord(
            existing.id(),
            existing.key(),
            existing.type(),
            newState,
            existing.status(),
            existing.attempt(),
            existing.maxAttempts(),
            existing.nextRunAt(),
            existing.leaseOwner(),
            existing.leaseUntil(),
            newPayload,
            existing.createdAt(),
            now,
            existing.lastError()
        );
    }

    private TaskState resolveInitialState(
        @NonNull TaskDefinition<?> definition,
        @NonNull TaskState initial,
        @NonNull TaskState incoming
    ) {
        TaskStateUpdate update = definition.stateMachine().resolveExternalState(initial, incoming);

        if (update.action() == TaskStateUpdateAction.APPLY) {
            return Optional.ofNullable(update.state())
                .orElseThrow(() -> new TaskStateException("Missing state for update"));
        }
        if (update.action() == TaskStateUpdateAction.REJECT) {
            String reason = Optional.ofNullable(update.reason()).orElse("Rejected state update");
            throw new TaskStateException(reason);
        }

        return initial;
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

    private @NonNull TaskSnapshot toSnapshot(
        @NonNull TaskRecord record,
        @NonNull List<TaskLink> links
    ) {
        return new TaskSnapshot(
            record.id(),
            record.key(),
            record.type(),
            record.state(),
            record.status(),
            record.attempt(),
            record.maxAttempts(),
            record.nextRunAt(),
            record.createdAt(),
            record.updatedAt(),
            record.payload(),
            links
        );
    }

    private @NonNull List<TaskLink> loadLinks(
        @NonNull TaskId taskId
    ) {
        return store.findLinks(taskId);
    }

    private @NonNull Instant now() {
        return Instant.now(clock);
    }

    private @NonNull TaskErrorInfo toErrorInfo(
        @NonNull TaskException error
    ) {
        Objects.requireNonNull(error, "error");

        String type = error.getClass().getSimpleName();
        String message = sanitizeMessage(error.getMessage());

        return new TaskErrorInfo(type, message);
    }

    private @NonNull String sanitizeMessage(
        @Nullable String message
    ) {
        if (message == null || message.isBlank()) {
            return "Task failed";
        }

        String sanitized = message.replaceAll("[\r\n]+", " ").trim();
        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 500);
        }

        return sanitized;
    }

    private boolean isChanged(
        @NonNull TaskRecord existing,
        @NonNull TaskRecord updated
    ) {
        if (!existing.state().equals(updated.state())) {
            return true;
        }
        if (!existing.payload().contentType().equals(updated.payload().contentType())) {
            return true;
        }

        return !Arrays.equals(existing.payload().data(), updated.payload().data());
    }

    private void completeWithEvent(
        @NonNull TaskRecord completed
    ) {
        Objects.requireNonNull(completed, "completed");

        if (sharedEventStore && transactionalEventStore != null) {
            transactionalEventStore.inTransaction(() -> {
                store.update(completed);
                emitCompletionEvent(completed, completed.updatedAt());
                return null;
            });
            return;
        }

        store.update(completed);
        emitCompletionEvent(completed, completed.updatedAt());
    }

    private void emitCompletionEvent(
        @NonNull TaskRecord terminalRecord,
        @NonNull Instant now
    ) {
        if (eventStore == null) {
            return;
        }
        if (store.hasDependents(terminalRecord.id())) {
            return;
        }

        List<TaskRecord> chainRecords = loadChainRecords(terminalRecord);
        List<TaskEventContextEntry> contexts = new ArrayList<>();
        for (TaskRecord record : chainRecords) {
            if (!shouldIncludeChainContext(record, terminalRecord)) {
                continue;
            }
            contexts.add(new TaskEventContextEntry(
                record.id(),
                record.type(),
                TaskEventContextKind.CHAIN,
                record.payload(),
                now
            ));
        }

        List<TaskId> chainTaskIds = new ArrayList<>();
        for (TaskRecord record : chainRecords) {
            chainTaskIds.add(record.id());
        }

        TaskEventRecord event = new TaskEventRecord(
            TaskEventId.random(),
            terminalRecord.id(),
            terminalRecord.key(),
            terminalRecord.type(),
            TaskEventType.CHAIN_COMPLETED,
            now
        );

        eventStore.createEvent(event, contexts, chainTaskIds);
    }

    private @NonNull List<TaskRecord> loadChainRecords(
        @NonNull TaskRecord terminalRecord
    ) {
        Map<TaskId, TaskRecord> records = new LinkedHashMap<>();
        Deque<TaskId> queue = new ArrayDeque<>();

        records.put(terminalRecord.id(), terminalRecord);
        queue.add(terminalRecord.id());

        while (!queue.isEmpty()) {
            TaskId current = queue.removeFirst();
            List<TaskLink> links = store.findLinks(current);
            for (TaskLink link : links) {
                if (link.type() != TaskLinkType.DEPENDS_ON) {
                    continue;
                }
                TaskId dependencyId = link.targetId();
                if (records.containsKey(dependencyId)) {
                    continue;
                }

                TaskRecord dependency = store.findById(dependencyId);
                if (dependency == null) {
                    continue;
                }

                records.put(dependencyId, dependency);
                queue.add(dependencyId);
            }
        }

        return List.copyOf(records.values());
    }

    private boolean shouldIncludeChainContext(
        @NonNull TaskRecord record,
        @NonNull TaskRecord terminalRecord
    ) {
        if (record.id().equals(terminalRecord.id())) {
            return true;
        }

        TaskDefinition<?> definition = definitions.get(record.type());
        return definition != null && definition.contributesToChainContext();
    }

    private void recordRequestContext(
        @NonNull TaskRecord record,
        @NonNull TaskPayload payload,
        @NonNull Instant now
    ) {
        if (eventStore == null) {
            return;
        }

        eventStore.recordRequestContext(record.id(), record.type(), payload, now);
    }

    private void recordDuplicateContext(
        @NonNull TaskRecord record,
        @NonNull TaskPayload payload,
        @NonNull Instant now
    ) {
        if (eventStore == null) {
            return;
        }

        eventStore.recordDuplicateContext(record.id(), record.type(), payload, now);
    }

    private <C> boolean shouldEmitLateEvent(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<C> definition
    ) {
        return record.status() == TaskStatus.COMPLETED
            && definition.stateMachine().isTerminal(record.state());
    }

    private void runEventTransaction(
        @NonNull Runnable action
    ) {
        Objects.requireNonNull(action, "action");

        if (transactionalEventStore == null) {
            action.run();
            return;
        }

        transactionalEventStore.inTransaction(() -> {
            action.run();
            return null;
        });
    }

    private <T> T withSharedTransaction(
        @NonNull Supplier<T> action
    ) {
        Objects.requireNonNull(action, "action");

        if (sharedEventStore && transactionalEventStore != null) {
            return transactionalEventStore.inTransaction(action);
        }

        return action.get();
    }

    private static final class UpdateTracker {
        private boolean changed;
    }
}
