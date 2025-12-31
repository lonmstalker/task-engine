package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventContextKind;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.event.TaskEventStore;
import io.lonmstalker.task.api.event.TaskEventTransactionalStore;
import io.lonmstalker.task.api.event.TaskEventType;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

final class StoreTaskEventPublisher implements TaskEventPublisher {

    private final @NonNull TaskStore store;
    private final @NonNull TaskEventStore eventStore;
    private final @NonNull Map<TaskType, TaskDefinition<?>> definitions;
    private final @NonNull TaskChainResolver chainResolver;
    private final @Nullable TaskEventTransactionalStore transactionalStore;
    private final boolean sharedEventStore;
    private final boolean closeEventStore;

    StoreTaskEventPublisher(
        @NonNull TaskStore store,
        @NonNull TaskEventStore eventStore,
        @NonNull Map<TaskType, TaskDefinition<?>> definitions
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.chainResolver = new TaskChainResolver(store);
        this.sharedEventStore = eventStore == store && eventStore instanceof TaskEventTransactionalStore;
        this.transactionalStore = eventStore instanceof TaskEventTransactionalStore
            ? (TaskEventTransactionalStore) eventStore
            : null;
        this.closeEventStore = eventStore != store;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public <T> T inSharedTransaction(
        @NonNull Supplier<T> action
    ) {
        Objects.requireNonNull(action, "action");

        if (sharedEventStore && transactionalStore != null) {
            return transactionalStore.inTransaction(action);
        }

        return action.get();
    }

    @Override
    public void onRequestCreated(
        @NonNull TaskRecord record,
        @NonNull TaskPayload payload,
        @NonNull Instant now
    ) {
        eventStore.recordRequestContext(record.id(), record.type(), payload, now);
    }

    @Override
    public void onDuplicate(
        @NonNull TaskRecord record,
        @NonNull TaskPayload payload,
        @NonNull Instant now,
        @NonNull TaskDefinition<?> definition
    ) {
        runEventTransaction(() -> {
            eventStore.recordDuplicateContext(record.id(), record.type(), payload, now);
            if (shouldEmitEvent(record, definition)) {
                emitEvent(record, now);
            }
        });
    }

    @Override
    public void onFinalized(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<?> definition,
        @NonNull Instant now
    ) {
        if (!shouldEmitEvent(record, definition)) {
            return;
        }

        runEventTransaction(() -> emitEvent(record, now));
    }

    @Override
    public void close() {
        if (closeEventStore) {
            eventStore.close();
        }
    }

    private void runEventTransaction(
        @NonNull Runnable action
    ) {
        Objects.requireNonNull(action, "action");

        if (transactionalStore == null || sharedEventStore) {
            action.run();
            return;
        }

        transactionalStore.inTransaction(() -> {
            action.run();
            return null;
        });
    }

    private boolean shouldEmitEvent(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<?> definition
    ) {
        if (record.status() == TaskStatus.FAILED) {
            return true;
        }
        if (record.status() != TaskStatus.COMPLETED) {
            return false;
        }
        return definition.stateMachine().isTerminal(record.state());
    }

    private void emitEvent(
        @NonNull TaskRecord terminalRecord,
        @NonNull Instant now
    ) {
        if (terminalRecord.status() != TaskStatus.FAILED
            && chainResolver.hasDependents(terminalRecord.id())) {
            return;
        }

        List<TaskRecord> chainRecords = chainResolver.loadChainRecords(terminalRecord);
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

        List<TaskId> attachTaskIds = chainTaskIds;
        if (terminalRecord.status() == TaskStatus.FAILED) {
            List<TaskId> dependents = chainResolver.loadDependentTaskIds(terminalRecord.id());
            if (!dependents.isEmpty()) {
                LinkedHashSet<TaskId> unique = new LinkedHashSet<>(chainTaskIds.size() + dependents.size());
                unique.addAll(chainTaskIds);
                unique.addAll(dependents);
                attachTaskIds = new ArrayList<>(unique);
            }
        }

        TaskEventRecord event = new TaskEventRecord(
            TaskEventId.random(),
            terminalRecord.id(),
            terminalRecord.key(),
            terminalRecord.type(),
            TaskEventType.CHAIN_COMPLETED,
            now
        );

        eventStore.createEvent(event, contexts, attachTaskIds);
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
}
