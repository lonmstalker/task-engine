package io.lonmstalker.task.impl.store.postgres;

import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.event.TaskEventTransactionalStore;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.store.TaskClaim;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskRecordUpdater;
import io.lonmstalker.task.api.store.TaskStore;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import net.jcip.annotations.ThreadSafe;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * PostgreSQL-backed task store.
 */
@ThreadSafe
public final class PostgresTaskStore implements TaskStore, TaskEventTransactionalStore {

    private final @NonNull PostgresTransactionManager transactionManager;
    private final @NonNull PostgresTaskRepository taskRepository;
    private final @NonNull PostgresEventRepository eventRepository;

    public PostgresTaskStore(
        @NonNull DataSource dataSource
    ) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.transactionManager = new PostgresTransactionManager(dataSource);
        this.taskRepository = new PostgresTaskRepository(transactionManager);
        this.eventRepository = new PostgresEventRepository(transactionManager);
    }

    @Override
    public @NonNull TaskRecord create(
        @NonNull TaskRecord record,
        @NonNull List<TaskLink> links
    ) {
        return taskRepository.create(record, links);
    }

    @Override
    public @NonNull TaskRecord updateOnDuplicate(
        @NonNull TaskKey key,
        @NonNull TaskRecordUpdater updater,
        @NonNull List<TaskLink> links
    ) {
        return taskRepository.updateOnDuplicate(key, updater, links);
    }

    @Override
    public @Nullable TaskRecord findByKey(
        @NonNull TaskKey key
    ) {
        return taskRepository.findByKey(key);
    }

    @Override
    public @Nullable TaskRecord findById(
        @NonNull TaskId id
    ) {
        return taskRepository.findById(id);
    }

    @Override
    public @NonNull List<TaskRecord> claim(
        @NonNull TaskClaim claim
    ) {
        return taskRepository.claim(claim);
    }

    @Override
    public @NonNull TaskRecord update(
        @NonNull TaskRecord record
    ) {
        return taskRepository.update(record);
    }

    @Override
    public @NonNull List<TaskLink> findLinks(
        @NonNull TaskId id
    ) {
        return taskRepository.findLinks(id);
    }

    @Override
    public boolean hasDependents(
        @NonNull TaskId id
    ) {
        return taskRepository.hasDependents(id);
    }

    @Override
    public void resetExpiredLeases(
        @NonNull Instant now
    ) {
        taskRepository.resetExpiredLeases(now);
    }

    @Override
    public void recordRequestContext(
        @NonNull TaskId taskId,
        @NonNull TaskType taskType,
        @NonNull TaskPayload payload,
        @NonNull Instant createdAt
    ) {
        eventRepository.recordRequestContext(taskId, taskType, payload, createdAt);
    }

    @Override
    public void recordDuplicateContext(
        @NonNull TaskId taskId,
        @NonNull TaskType taskType,
        @NonNull TaskPayload payload,
        @NonNull Instant createdAt
    ) {
        eventRepository.recordDuplicateContext(taskId, taskType, payload, createdAt);
    }

    @Override
    public @NonNull TaskEventRecord createEvent(
        @NonNull TaskEventRecord record,
        @NonNull List<TaskEventContextEntry> contexts,
        @NonNull List<TaskId> attachTaskIds
    ) {
        return eventRepository.createEvent(record, contexts, attachTaskIds);
    }

    @Override
    public @NonNull List<TaskEventRecord> findEventsByTaskId(
        @NonNull TaskId taskId
    ) {
        return eventRepository.findEventsByTaskId(taskId);
    }

    @Override
    public @NonNull List<TaskEventContextEntry> findContexts(
        @NonNull TaskEventId eventId
    ) {
        return eventRepository.findContexts(eventId);
    }

    @Override
    public <T> T inTransaction(
        @NonNull Supplier<T> action
    ) {
        return transactionManager.inTransaction(action);
    }

    @Override
    public void close() {
    }
}
