package io.lonmstalker.task.api.event;

import io.lonmstalker.task.api.annotations.Blocking;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskType;
import java.time.Instant;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Persistent store for task events and contexts.
 */
public interface TaskEventStore extends AutoCloseable {

    /**
     * Records initial request context for a task.
     *
     * @param taskId task id
     * @param taskType task type
     * @param payload request payload
     * @param createdAt context creation time
     */
    @Blocking
    void recordRequestContext(
        @NonNull TaskId taskId,
        @NonNull TaskType taskType,
        @NonNull TaskPayload payload,
        @NonNull Instant createdAt
    );

    /**
     * Records duplicate request context for a task.
     *
     * @param taskId task id
     * @param taskType task type
     * @param payload duplicate payload
     * @param createdAt context creation time
     */
    @Blocking
    void recordDuplicateContext(
        @NonNull TaskId taskId,
        @NonNull TaskType taskType,
        @NonNull TaskPayload payload,
        @NonNull Instant createdAt
    );

    /**
     * Creates event and attaches pending request/duplicate contexts for the given tasks.
     *
     * @param record event record
     * @param contexts event-specific contexts to store immediately
     * @param attachTaskIds task ids to attach pending contexts for
     * @return stored event record
     */
    @Blocking
    @NonNull TaskEventRecord createEvent(
        @NonNull TaskEventRecord record,
        @NonNull List<TaskEventContextEntry> contexts,
        @NonNull List<TaskId> attachTaskIds
    );

    /**
     * Finds events for task id.
     *
     * @param taskId task id
     * @return event records
     */
    @Blocking
    @NonNull List<TaskEventRecord> findEventsByTaskId(
        @NonNull TaskId taskId
    );

    /**
     * Loads event contexts.
     *
     * @param eventId event id
     * @return context entries
     */
    @Blocking
    @NonNull List<TaskEventContextEntry> findContexts(
        @NonNull TaskEventId eventId
    );

    @Override
    void close();
}
