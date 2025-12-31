package io.lonmstalker.task.impl.store.memory;

import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventContextKind;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.event.TaskEventTransactionalStore;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.jcip.annotations.ThreadSafe;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * In-memory event store for tests.
 */
@ThreadSafe
public final class InMemoryTaskEventStore implements TaskEventTransactionalStore {

    private final @NonNull Map<TaskEventId, TaskEventRecord> events = new ConcurrentHashMap<>();
    private final @NonNull Map<TaskEventId, List<TaskEventContextEntry>> contexts = new ConcurrentHashMap<>();
    private final @NonNull Map<TaskId, List<TaskEventRecord>> eventsByTask = new ConcurrentHashMap<>();
    private final @NonNull List<TaskEventContextEntry> pending = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void recordRequestContext(
        @NonNull TaskId taskId,
        @NonNull TaskType taskType,
        @NonNull TaskPayload payload,
        @NonNull Instant createdAt
    ) {
        pending.add(new TaskEventContextEntry(
            taskId,
            taskType,
            TaskEventContextKind.REQUEST,
            payload,
            createdAt
        ));
    }

    @Override
    public void recordDuplicateContext(
        @NonNull TaskId taskId,
        @NonNull TaskType taskType,
        @NonNull TaskPayload payload,
        @NonNull Instant createdAt
    ) {
        pending.add(new TaskEventContextEntry(
            taskId,
            taskType,
            TaskEventContextKind.DUPLICATE,
            payload,
            createdAt
        ));
    }

    @Override
    public @NonNull TaskEventRecord createEvent(
        @NonNull TaskEventRecord record,
        @NonNull List<TaskEventContextEntry> contexts,
        @NonNull List<TaskId> attachTaskIds
    ) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(contexts, "contexts");
        Objects.requireNonNull(attachTaskIds, "attachTaskIds");

        Set<TaskId> attachSet = new HashSet<>(attachTaskIds);
        List<TaskEventContextEntry> attached = new ArrayList<>(contexts);

        synchronized (pending) {
            Iterator<TaskEventContextEntry> iterator = pending.iterator();
            while (iterator.hasNext()) {
                TaskEventContextEntry entry = iterator.next();
                if (attachSet.contains(entry.taskId())) {
                    attached.add(entry);
                    iterator.remove();
                }
            }
        }

        events.put(record.id(), record);
        this.contexts.put(record.id(), List.copyOf(attached));

        eventsByTask.compute(record.taskId(), (taskId, existing) -> {
            List<TaskEventRecord> updated = new ArrayList<>();
            if (existing != null) {
                updated.addAll(existing);
            }
            updated.add(record);
            return List.copyOf(updated);
        });

        return record;
    }

    @Override
    public @NonNull List<TaskEventRecord> findEventsByTaskId(
        @NonNull TaskId taskId
    ) {
        List<TaskEventRecord> records = eventsByTask.get(taskId);
        if (records == null) {
            return List.of();
        }
        return List.copyOf(records);
    }

    @Override
    public @NonNull List<TaskEventContextEntry> findContexts(
        @NonNull TaskEventId eventId
    ) {
        List<TaskEventContextEntry> entries = contexts.get(eventId);
        if (entries == null) {
            return List.of();
        }
        return List.copyOf(entries);
    }

    @Override
    public <T> T inTransaction(
        @NonNull Supplier<T> action
    ) {
        return action.get();
    }

    @Override
    public void close() {
    }
}
