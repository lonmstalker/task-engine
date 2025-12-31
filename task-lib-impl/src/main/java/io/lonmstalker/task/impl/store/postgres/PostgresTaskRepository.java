package io.lonmstalker.task.impl.store.postgres;

import io.lonmstalker.task.api.error.TaskDuplicateException;
import io.lonmstalker.task.api.error.TaskStoreException;
import io.lonmstalker.task.api.model.TaskErrorInfo;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskLinkType;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.store.TaskClaim;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskRecordUpdater;
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

final class PostgresTaskRepository {

    private static final @NonNull String INSERT_TASK_SQL = """
        INSERT INTO task_tasks (
            id,
            task_key,
            task_type,
            task_state,
            task_status,
            attempt,
            max_attempts,
            next_run_at,
            lease_owner,
            lease_until,
            payload,
            payload_content_type,
            created_at,
            updated_at,
            last_error_type,
            last_error_message
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final @NonNull String UPDATE_TASK_SQL = """
        UPDATE task_tasks
        SET task_state = ?,
            task_status = ?,
            attempt = ?,
            max_attempts = ?,
            next_run_at = ?,
            lease_owner = ?,
            lease_until = ?,
            payload = ?,
            payload_content_type = ?,
            updated_at = ?,
            last_error_type = ?,
            last_error_message = ?
        WHERE id = ?
        """;

    private static final @NonNull String SELECT_BY_KEY_FOR_UPDATE_SQL = """
        SELECT *
        FROM task_tasks
        WHERE task_key = ?
        FOR UPDATE
        """;

    private static final @NonNull String SELECT_BY_KEY_SQL = """
        SELECT *
        FROM task_tasks
        WHERE task_key = ?
        """;

    private static final @NonNull String SELECT_BY_ID_SQL = """
        SELECT *
        FROM task_tasks
        WHERE id = ?
        """;

    private static final @NonNull String CLAIM_SQL = """
        WITH candidate AS (
            SELECT t.id
            FROM task_tasks t
            WHERE t.task_status IN ('PENDING', 'WAITING_RETRY')
              AND (t.next_run_at IS NULL OR t.next_run_at <= ?)
              AND (t.lease_until IS NULL OR t.lease_until <= ?)
              AND NOT EXISTS (
                  SELECT 1
                  FROM task_links l
                  JOIN task_tasks dep ON dep.id = l.linked_task_id
                  WHERE l.task_id = t.id
                    AND l.link_type = 'DEPENDS_ON'
                    AND dep.task_status <> 'COMPLETED'
              )
            ORDER BY t.created_at
            FOR UPDATE SKIP LOCKED
            LIMIT ?
        )
        UPDATE task_tasks t
        SET task_status = 'RUNNING',
            attempt = t.attempt + 1,
            lease_owner = ?,
            lease_until = ?,
            updated_at = ?
        FROM candidate c
        WHERE t.id = c.id
        RETURNING t.*
        """;

    private static final @NonNull String RESET_EXPIRED_LEASES_SQL = """
        UPDATE task_tasks
        SET task_status = 'PENDING',
            lease_owner = NULL,
            lease_until = NULL,
            updated_at = ?
        WHERE task_status = 'RUNNING'
          AND lease_until IS NOT NULL
          AND lease_until <= ?
        """;

    private static final @NonNull String INSERT_LINK_SQL = """
        INSERT INTO task_links (task_id, linked_task_id, link_type)
        VALUES (?, ?, ?)
        ON CONFLICT DO NOTHING
        """;

    private static final @NonNull String SELECT_LINKS_SQL = """
        SELECT linked_task_id, link_type
        FROM task_links
        WHERE task_id = ?
        """;

    private static final @NonNull String HAS_DEPENDENTS_SQL = """
        SELECT 1
        FROM task_links
        WHERE linked_task_id = ?
          AND link_type = 'DEPENDS_ON'
        LIMIT 1
        """;

    private final @NonNull PostgresTransactionManager transactionManager;

    PostgresTaskRepository(
        @NonNull PostgresTransactionManager transactionManager
    ) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    @NonNull TaskRecord create(
        @NonNull TaskRecord record,
        @NonNull List<TaskLink> links
    ) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(links, "links");

        try {
            return transactionManager.inLocalTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(INSERT_TASK_SQL)) {
                    bindTask(statement, record);
                    statement.executeUpdate();
                }

                insertLinks(connection, record.id(), links);

                return record;
            });
        } catch (SQLException e) {
            if (isDuplicateKey(e)) {
                throw new TaskDuplicateException("Task already exists", e);
            }
            throw new TaskStoreException("Failed to create task", e);
        }
    }

    @NonNull TaskRecord updateOnDuplicate(
        @NonNull TaskKey key,
        @NonNull TaskRecordUpdater updater,
        @NonNull List<TaskLink> links
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(updater, "updater");
        Objects.requireNonNull(links, "links");

        try {
            return transactionManager.inLocalTransaction(connection -> {
                TaskRecord existing = null;
                try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_KEY_FOR_UPDATE_SQL)) {
                    statement.setString(1, key.value());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            existing = mapRecord(resultSet);
                        }
                    }
                }

                if (existing == null) {
                    throw new TaskStoreException("Task not found for key: " + key.value());
                }

                TaskRecord updated = updater.update(existing);
                try (PreparedStatement statement = connection.prepareStatement(UPDATE_TASK_SQL)) {
                    bindTaskUpdate(statement, updated);
                    statement.setObject(13, updated.id().value());
                    statement.executeUpdate();
                }

                insertLinks(connection, existing.id(), links);

                return updated;
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to update duplicate task", e);
        }
    }

    @Nullable TaskRecord findByKey(
        @NonNull TaskKey key
    ) {
        Objects.requireNonNull(key, "key");

        try {
            return transactionManager.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_KEY_SQL)) {
                    statement.setString(1, key.value());

                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return mapRecord(resultSet);
                        }
                    }

                    return null;
                }
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to find task by key", e);
        }
    }

    @Nullable TaskRecord findById(
        @NonNull TaskId id
    ) {
        Objects.requireNonNull(id, "id");

        try {
            return transactionManager.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_SQL)) {
                    statement.setObject(1, id.value());

                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return mapRecord(resultSet);
                        }
                    }

                    return null;
                }
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to find task by id", e);
        }
    }

    @NonNull List<TaskRecord> claim(
        @NonNull TaskClaim claim
    ) {
        Objects.requireNonNull(claim, "claim");

        Instant leaseUntil = claim.now().plus(claim.leaseDuration());
        List<TaskRecord> records = new ArrayList<>();

        try {
            return transactionManager.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(CLAIM_SQL)) {
                    statement.setTimestamp(1, toTimestamp(claim.now()));
                    statement.setTimestamp(2, toTimestamp(claim.now()));
                    statement.setInt(3, claim.maxTasks());
                    statement.setString(4, claim.leaseOwner());
                    statement.setTimestamp(5, toTimestamp(leaseUntil));
                    statement.setTimestamp(6, toTimestamp(claim.now()));

                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            records.add(mapRecord(resultSet));
                        }
                    }

                    return List.copyOf(records);
                }
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to claim tasks", e);
        }
    }

    @NonNull TaskRecord update(
        @NonNull TaskRecord record
    ) {
        Objects.requireNonNull(record, "record");

        try {
            return transactionManager.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(UPDATE_TASK_SQL)) {
                    bindTaskUpdate(statement, record);
                    statement.setObject(13, record.id().value());
                    statement.executeUpdate();
                }

                return record;
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to update task", e);
        }
    }

    @NonNull List<TaskLink> findLinks(
        @NonNull TaskId id
    ) {
        Objects.requireNonNull(id, "id");

        List<TaskLink> links = new ArrayList<>();

        try {
            return transactionManager.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(SELECT_LINKS_SQL)) {
                    statement.setObject(1, id.value());

                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            UUID linkedId = (UUID) resultSet.getObject("linked_task_id");
                            String type = resultSet.getString("link_type");
                            links.add(new TaskLink(new TaskId(linkedId), TaskLinkType.valueOf(type)));
                        }
                    }

                    return List.copyOf(links);
                }
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to load task links", e);
        }
    }

    boolean hasDependents(
        @NonNull TaskId id
    ) {
        Objects.requireNonNull(id, "id");

        try {
            return transactionManager.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(HAS_DEPENDENTS_SQL)) {
                    statement.setObject(1, id.value());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next();
                    }
                }
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to check dependents", e);
        }
    }

    void resetExpiredLeases(
        @NonNull Instant now
    ) {
        Objects.requireNonNull(now, "now");

        try {
            transactionManager.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(RESET_EXPIRED_LEASES_SQL)) {
                    statement.setTimestamp(1, toTimestamp(now));
                    statement.setTimestamp(2, toTimestamp(now));
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to reset expired leases", e);
        }
    }

    private void insertLinks(
        @NonNull Connection connection,
        @NonNull TaskId taskId,
        @NonNull List<TaskLink> links
    ) throws SQLException {
        if (links.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(INSERT_LINK_SQL)) {
            for (TaskLink link : links) {
                statement.setObject(1, taskId.value());
                statement.setObject(2, link.targetId().value());
                statement.setString(3, link.type().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void bindTask(
        @NonNull PreparedStatement statement,
        @NonNull TaskRecord record
    ) throws SQLException {
        statement.setObject(1, record.id().value());
        statement.setString(2, record.key().value());
        statement.setString(3, record.type().value());
        statement.setString(4, record.state().value());
        statement.setString(5, record.status().name());
        statement.setInt(6, record.attempt());
        statement.setInt(7, record.maxAttempts());
        setInstant(statement, 8, record.nextRunAt());
        statement.setString(9, record.leaseOwner());
        setInstant(statement, 10, record.leaseUntil());
        statement.setBytes(11, record.payload().data());
        statement.setString(12, record.payload().contentType());
        setInstant(statement, 13, record.createdAt());
        setInstant(statement, 14, record.updatedAt());
        setErrorInfo(statement, 15, 16, record.lastError());
    }

    private void bindTaskUpdate(
        @NonNull PreparedStatement statement,
        @NonNull TaskRecord record
    ) throws SQLException {
        statement.setString(1, record.state().value());
        statement.setString(2, record.status().name());
        statement.setInt(3, record.attempt());
        statement.setInt(4, record.maxAttempts());
        setInstant(statement, 5, record.nextRunAt());
        statement.setString(6, record.leaseOwner());
        setInstant(statement, 7, record.leaseUntil());
        statement.setBytes(8, record.payload().data());
        statement.setString(9, record.payload().contentType());
        setInstant(statement, 10, record.updatedAt());
        setErrorInfo(statement, 11, 12, record.lastError());
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

        statement.setTimestamp(index, toTimestamp(instant));
    }

    private void setErrorInfo(
        @NonNull PreparedStatement statement,
        int typeIndex,
        int messageIndex,
        @Nullable TaskErrorInfo error
    ) throws SQLException {
        if (error == null) {
            statement.setString(typeIndex, null);
            statement.setString(messageIndex, null);
            return;
        }

        statement.setString(typeIndex, error.type());
        statement.setString(messageIndex, error.message());
    }

    private @NonNull TaskRecord mapRecord(
        @NonNull ResultSet resultSet
    ) throws SQLException {
        TaskId id = new TaskId((UUID) resultSet.getObject("id"));
        TaskKey key = new TaskKey(resultSet.getString("task_key"));
        TaskType type = new TaskType(resultSet.getString("task_type"));
        TaskState state = new TaskState(resultSet.getString("task_state"));
        TaskStatus status = TaskStatus.valueOf(resultSet.getString("task_status"));
        int attempt = resultSet.getInt("attempt");
        int maxAttempts = resultSet.getInt("max_attempts");
        Instant nextRunAt = toInstant(resultSet.getTimestamp("next_run_at"));
        String leaseOwner = resultSet.getString("lease_owner");
        Instant leaseUntil = toInstant(resultSet.getTimestamp("lease_until"));
        byte[] payloadData = resultSet.getBytes("payload");
        String contentType = resultSet.getString("payload_content_type");
        Instant createdAt = toInstant(resultSet.getTimestamp("created_at"));
        Instant updatedAt = toInstant(resultSet.getTimestamp("updated_at"));
        String errorType = resultSet.getString("last_error_type");
        String errorMessage = resultSet.getString("last_error_message");

        TaskErrorInfo error = null;
        if (errorType != null && errorMessage != null) {
            error = new TaskErrorInfo(errorType, errorMessage);
        }

        TaskPayload payload = new TaskPayload(payloadData, contentType);

        return new TaskRecord(
            id,
            key,
            type,
            state,
            status,
            attempt,
            maxAttempts,
            nextRunAt,
            leaseOwner,
            leaseUntil,
            payload,
            createdAt,
            updatedAt,
            error
        );
    }

    private @Nullable Instant toInstant(
        @Nullable Timestamp timestamp
    ) {
        if (timestamp == null) {
            return null;
        }

        return timestamp.toInstant();
    }

    private @NonNull Timestamp toTimestamp(
        @NonNull Instant instant
    ) {
        return Timestamp.from(instant);
    }

    private boolean isDuplicateKey(
        @NonNull SQLException exception
    ) {
        return "23505".equals(exception.getSQLState());
    }
}
