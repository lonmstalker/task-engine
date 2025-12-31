package io.lonmstalker.task.impl.store.postgres;

import io.lonmstalker.task.api.error.TaskStoreException;
import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventContextKind;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.event.TaskEventType;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskType;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

final class PostgresEventRepository {

    private static final @NonNull String INSERT_EVENT_SQL = """
        INSERT INTO task_event_outbox (
            id,
            task_id,
            task_key,
            task_type,
            event_type,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?)
        """;

    private static final @NonNull String INSERT_EVENT_CONTEXT_SQL = """
        INSERT INTO task_event_contexts (
            event_id,
            task_id,
            task_type,
            context_kind,
            payload,
            payload_content_type,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    private static final @NonNull String SELECT_EVENTS_BY_TASK_SQL = """
        SELECT *
        FROM task_event_outbox
        WHERE task_id = ?
        ORDER BY created_at
        """;

    private static final @NonNull String SELECT_CONTEXTS_BY_EVENT_SQL = """
        SELECT *
        FROM task_event_contexts
        WHERE event_id = ?
        ORDER BY id
        """;

    private static final @NonNull String ATTACH_PENDING_CONTEXTS_SQL = """
        UPDATE task_event_contexts
        SET event_id = ?
        WHERE event_id IS NULL
          AND context_kind IN ('REQUEST', 'DUPLICATE')
          AND task_id = ANY (?)
        """;

    private final @NonNull PostgresTransactionManager transactionManager;

    PostgresEventRepository(
        @NonNull PostgresTransactionManager transactionManager
    ) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    void recordRequestContext(
        @NonNull TaskId taskId,
        @NonNull TaskType taskType,
        @NonNull TaskPayload payload,
        @NonNull Instant createdAt
    ) {
        TaskEventContextEntry entry = new TaskEventContextEntry(
            taskId,
            taskType,
            TaskEventContextKind.REQUEST,
            payload,
            createdAt
        );

        insertEventContext(null, entry);
    }

    void recordDuplicateContext(
        @NonNull TaskId taskId,
        @NonNull TaskType taskType,
        @NonNull TaskPayload payload,
        @NonNull Instant createdAt
    ) {
        TaskEventContextEntry entry = new TaskEventContextEntry(
            taskId,
            taskType,
            TaskEventContextKind.DUPLICATE,
            payload,
            createdAt
        );

        insertEventContext(null, entry);
    }

    @NonNull TaskEventRecord createEvent(
        @NonNull TaskEventRecord record,
        @NonNull List<TaskEventContextEntry> contexts,
        @NonNull List<TaskId> attachTaskIds
    ) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(contexts, "contexts");
        Objects.requireNonNull(attachTaskIds, "attachTaskIds");

        try {
            return transactionManager.inLocalTransaction(connection -> {
                insertEvent(connection, record);
                insertEventContexts(connection, record.id(), contexts);
                attachPendingContexts(connection, record.id(), attachTaskIds);
                return record;
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to create event", e);
        }
    }

    @NonNull List<TaskEventRecord> findEventsByTaskId(
        @NonNull TaskId taskId
    ) {
        Objects.requireNonNull(taskId, "taskId");

        List<TaskEventRecord> records = new ArrayList<>();

        try {
            return transactionManager.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(SELECT_EVENTS_BY_TASK_SQL)) {
                    statement.setObject(1, taskId.value());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            records.add(mapEventRecord(resultSet));
                        }
                    }
                    return List.copyOf(records);
                }
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to find events by task id", e);
        }
    }

    @NonNull List<TaskEventContextEntry> findContexts(
        @NonNull TaskEventId eventId
    ) {
        Objects.requireNonNull(eventId, "eventId");

        List<TaskEventContextEntry> entries = new ArrayList<>();

        try {
            return transactionManager.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(SELECT_CONTEXTS_BY_EVENT_SQL)) {
                    statement.setObject(1, eventId.value());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            entries.add(mapEventContext(resultSet));
                        }
                    }
                    return List.copyOf(entries);
                }
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to load event contexts", e);
        }
    }

    private void insertEvent(
        @NonNull Connection connection,
        @NonNull TaskEventRecord record
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EVENT_SQL)) {
            statement.setObject(1, record.id().value());
            statement.setObject(2, record.taskId().value());
            statement.setString(3, record.taskKey().value());
            statement.setString(4, record.taskType().value());
            statement.setString(5, record.type().name());
            setInstant(statement, 6, record.createdAt());
            statement.executeUpdate();
        }
    }

    private void insertEventContexts(
        @NonNull Connection connection,
        @NonNull TaskEventId eventId,
        @NonNull List<TaskEventContextEntry> contexts
    ) throws SQLException {
        if (contexts.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(INSERT_EVENT_CONTEXT_SQL)) {
            for (TaskEventContextEntry entry : contexts) {
                bindEventContext(statement, eventId, entry);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertEventContext(
        @Nullable TaskEventId eventId,
        @NonNull TaskEventContextEntry entry
    ) {
        Objects.requireNonNull(entry, "entry");

        try {
            transactionManager.withConnection(connection -> {
                insertEventContext(connection, eventId, entry);
                return null;
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to insert event context", e);
        }
    }

    private void insertEventContext(
        @NonNull Connection connection,
        @Nullable TaskEventId eventId,
        @NonNull TaskEventContextEntry entry
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EVENT_CONTEXT_SQL)) {
            bindEventContext(statement, eventId, entry);
            statement.executeUpdate();
        }
    }

    private void bindEventContext(
        @NonNull PreparedStatement statement,
        @Nullable TaskEventId eventId,
        @NonNull TaskEventContextEntry entry
    ) throws SQLException {
        if (eventId == null) {
            statement.setObject(1, null);
        } else {
            statement.setObject(1, eventId.value());
        }
        statement.setObject(2, entry.taskId().value());
        statement.setString(3, entry.taskType().value());
        statement.setString(4, entry.kind().name());
        statement.setBytes(5, entry.payload().data());
        statement.setString(6, entry.payload().contentType());
        setInstant(statement, 7, entry.createdAt());
    }

    private void attachPendingContexts(
        @NonNull Connection connection,
        @NonNull TaskEventId eventId,
        @NonNull List<TaskId> taskIds
    ) throws SQLException {
        if (taskIds.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(ATTACH_PENDING_CONTEXTS_SQL)) {
            statement.setObject(1, eventId.value());
            Array taskIdArray = connection.createArrayOf(
                "uuid",
                taskIds.stream().map(TaskId::value).toArray(UUID[]::new)
            );
            try {
                statement.setArray(2, taskIdArray);
                statement.executeUpdate();
            } finally {
                taskIdArray.free();
            }
        }
    }

    private @NonNull TaskEventRecord mapEventRecord(
        @NonNull ResultSet resultSet
    ) throws SQLException {
        TaskEventId id = new TaskEventId((UUID) resultSet.getObject("id"));
        TaskId taskId = new TaskId((UUID) resultSet.getObject("task_id"));
        TaskKey taskKey = new TaskKey(resultSet.getString("task_key"));
        TaskType taskType = new TaskType(resultSet.getString("task_type"));
        TaskEventType type = TaskEventType.valueOf(resultSet.getString("event_type"));
        Instant createdAt = toInstant(resultSet.getTimestamp("created_at"));

        return new TaskEventRecord(id, taskId, taskKey, taskType, type, createdAt);
    }

    private @NonNull TaskEventContextEntry mapEventContext(
        @NonNull ResultSet resultSet
    ) throws SQLException {
        TaskId taskId = new TaskId((UUID) resultSet.getObject("task_id"));
        TaskType taskType = new TaskType(resultSet.getString("task_type"));
        TaskEventContextKind kind = TaskEventContextKind.valueOf(resultSet.getString("context_kind"));
        byte[] payloadData = resultSet.getBytes("payload");
        String contentType = resultSet.getString("payload_content_type");
        Instant createdAt = toInstant(resultSet.getTimestamp("created_at"));

        TaskPayload payload = new TaskPayload(payloadData, contentType);

        return new TaskEventContextEntry(taskId, taskType, kind, payload, createdAt);
    }

    private @Nullable Instant toInstant(
        @Nullable Timestamp timestamp
    ) {
        if (timestamp == null) {
            return null;
        }

        return timestamp.toInstant();
    }

    private void setInstant(
        @NonNull PreparedStatement statement,
        int index,
        @Nullable Instant instant
    ) throws SQLException {
        if (instant == null) {
            statement.setTimestamp(index, null);
            return;
        }

        statement.setTimestamp(index, Timestamp.from(instant));
    }
}
