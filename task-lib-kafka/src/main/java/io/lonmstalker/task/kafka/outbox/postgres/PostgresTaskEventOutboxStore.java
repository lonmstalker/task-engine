package io.lonmstalker.task.kafka.outbox.postgres;

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
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxClaim;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStore;
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
import javax.sql.DataSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * PostgreSQL outbox store for task events.
 */
public final class PostgresTaskEventOutboxStore implements TaskEventOutboxStore {

    private static final @NonNull String CLAIM_SQL = """
        WITH candidate AS (
            SELECT o.id
            FROM task_event_outbox o
            WHERE o.published_at IS NULL
              AND (o.lease_until IS NULL OR o.lease_until <= ?)
            ORDER BY o.created_at
            FOR UPDATE SKIP LOCKED
            LIMIT ?
        )
        UPDATE task_event_outbox o
        SET lease_owner = ?,
            lease_until = ?,
            publish_attempts = o.publish_attempts + 1
        FROM candidate c
        WHERE o.id = c.id
        RETURNING o.*
        """;

    private static final @NonNull String SELECT_CONTEXTS_SQL = """
        SELECT *
        FROM task_event_contexts
        WHERE event_id = ?
        ORDER BY id
        """;

    private static final @NonNull String MARK_PUBLISHED_SQL = """
        UPDATE task_event_outbox
        SET published_at = ?,
            lease_owner = NULL,
            lease_until = NULL
        WHERE id = ?
          AND lease_owner = ?
        """;

    private static final @NonNull String RELEASE_LEASE_SQL = """
        UPDATE task_event_outbox
        SET lease_owner = NULL,
            lease_until = ?
        WHERE id = ?
          AND lease_owner = ?
        """;

    private final @NonNull DataSource dataSource;

    public PostgresTaskEventOutboxStore(
        @NonNull DataSource dataSource
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public @NonNull List<TaskEventRecord> claim(
        @NonNull TaskEventOutboxClaim claim
    ) {
        Objects.requireNonNull(claim, "claim");

        List<TaskEventRecord> records = new ArrayList<>();
        Instant leaseUntil = claim.now().plus(claim.leaseDuration());

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(CLAIM_SQL)) {
            statement.setTimestamp(1, toTimestamp(claim.now()));
            statement.setInt(2, claim.maxEvents());
            statement.setString(3, claim.leaseOwner());
            statement.setTimestamp(4, toTimestamp(leaseUntil));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapEventRecord(resultSet));
                }
            }

            return List.copyOf(records);
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to claim outbox events", e);
        }
    }

    @Override
    public @NonNull List<TaskEventContextEntry> loadContexts(
        @NonNull TaskEventId eventId
    ) {
        Objects.requireNonNull(eventId, "eventId");

        List<TaskEventContextEntry> entries = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(SELECT_CONTEXTS_SQL)) {
            statement.setObject(1, eventId.value());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(mapEventContext(resultSet));
                }
            }

            return List.copyOf(entries);
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to load event contexts", e);
        }
    }

    @Override
    public void markPublished(
        @NonNull TaskEventId eventId,
        @NonNull String leaseOwner,
        @NonNull Instant publishedAt
    ) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseOwner, "leaseOwner");
        Objects.requireNonNull(publishedAt, "publishedAt");

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(MARK_PUBLISHED_SQL)) {
            statement.setTimestamp(1, toTimestamp(publishedAt));
            statement.setObject(2, eventId.value());
            statement.setString(3, leaseOwner);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to mark outbox event as published", e);
        }
    }

    @Override
    public void release(
        @NonNull TaskEventId eventId,
        @NonNull String leaseOwner,
        @NonNull Instant releasedAt
    ) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseOwner, "leaseOwner");
        Objects.requireNonNull(releasedAt, "releasedAt");

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(RELEASE_LEASE_SQL)) {
            statement.setTimestamp(1, toTimestamp(releasedAt));
            statement.setObject(2, eventId.value());
            statement.setString(3, leaseOwner);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to release outbox lease", e);
        }
    }

    @Override
    public void close() {
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

    private @NonNull Timestamp toTimestamp(
        @NonNull Instant instant
    ) {
        return Timestamp.from(instant);
    }
}
