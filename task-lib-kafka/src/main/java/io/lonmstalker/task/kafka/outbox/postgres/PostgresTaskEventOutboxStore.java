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
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxAdminStore;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxBatchStore;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxClaim;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStats;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStatsProvider;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxMaintenanceStore;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL outbox store for task events.
 */
public final class PostgresTaskEventOutboxStore implements TaskEventOutboxStore, TaskEventOutboxAdminStore,
    TaskEventOutboxBatchStore, TaskEventOutboxStatsProvider, TaskEventOutboxMaintenanceStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresTaskEventOutboxStore.class);

    private static final @NonNull String CLAIM_SQL = """
        WITH candidate AS (
            SELECT o.id
            FROM task_event_outbox o
            WHERE o.published_at IS NULL
              AND o.dead_letter_at IS NULL
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

    private static final @NonNull String SELECT_BATCH_CONTEXTS_SQL = """
        SELECT *
        FROM task_event_contexts
        WHERE event_id = ANY (?)
        ORDER BY event_id, id
        """;

    private static final @NonNull String SELECT_ATTEMPTS_SQL = """
        SELECT publish_attempts
        FROM task_event_outbox
        WHERE id = ?
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

    private static final @NonNull String MARK_DEAD_LETTER_SQL = """
        UPDATE task_event_outbox
        SET dead_letter_at = ?,
            dead_letter_reason = ?,
            lease_owner = NULL,
            lease_until = NULL
        WHERE id = ?
          AND lease_owner = ?
        """;

    private static final @NonNull String LOAD_STATS_SQL = """
        SELECT
            COUNT(*) FILTER (WHERE published_at IS NULL AND dead_letter_at IS NULL) AS pending,
            COUNT(*) FILTER (WHERE dead_letter_at IS NOT NULL) AS dead_lettered
        FROM task_event_outbox
        """;

    private static final @NonNull String PURGE_PUBLISHED_SQL = """
        DELETE FROM task_event_outbox
        WHERE (published_at IS NOT NULL AND published_at < ?)
           OR (dead_letter_at IS NOT NULL AND dead_letter_at < ?)
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
    public @NonNull Map<TaskEventId, List<TaskEventContextEntry>> loadContexts(
        @NonNull List<TaskEventId> eventIds
    ) {
        Objects.requireNonNull(eventIds, "eventIds");
        if (eventIds.isEmpty()) {
            return Map.of();
        }

        Map<TaskEventId, List<TaskEventContextEntry>> contexts = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(SELECT_BATCH_CONTEXTS_SQL)) {
            UUID[] ids = eventIds.stream().map(TaskEventId::value).toArray(UUID[]::new);
            java.sql.Array array = connection.createArrayOf("uuid", ids);
            try {
                statement.setArray(1, array);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        TaskEventId eventId = new TaskEventId((UUID) resultSet.getObject("event_id"));
                        contexts.computeIfAbsent(eventId, key -> new ArrayList<>())
                            .add(mapEventContext(resultSet));
                    }
                }
            } finally {
                array.free();
            }

            return Map.copyOf(contexts);
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to load event contexts batch", e);
        }
    }

    @Override
    public int loadPublishAttempts(
        @NonNull TaskEventId eventId
    ) {
        Objects.requireNonNull(eventId, "eventId");

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(SELECT_ATTEMPTS_SQL)) {
            statement.setObject(1, eventId.value());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("publish_attempts");
                }
            }

            return 0;
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to load publish attempts", e);
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
            int updated = statement.executeUpdate();
            if (updated == 0) {
                LOGGER.warn("Outbox event {} not marked published due to lease mismatch", eventId.value());
            }
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
            int updated = statement.executeUpdate();
            if (updated == 0) {
                LOGGER.warn("Outbox event {} not released due to lease mismatch", eventId.value());
            }
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to release outbox lease", e);
        }
    }

    @Override
    public void markDeadLetter(
        @NonNull TaskEventId eventId,
        @NonNull String leaseOwner,
        @NonNull Instant deadLetterAt,
        @NonNull String reason
    ) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseOwner, "leaseOwner");
        Objects.requireNonNull(deadLetterAt, "deadLetterAt");
        Objects.requireNonNull(reason, "reason");

        String trimmed = reason.length() > 1000 ? reason.substring(0, 1000) : reason;

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(MARK_DEAD_LETTER_SQL)) {
            statement.setTimestamp(1, toTimestamp(deadLetterAt));
            statement.setString(2, trimmed);
            statement.setObject(3, eventId.value());
            statement.setString(4, leaseOwner);
            int updated = statement.executeUpdate();
            if (updated == 0) {
                LOGGER.warn("Outbox event {} not marked dead-letter due to lease mismatch", eventId.value());
            }
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to mark outbox event as dead-letter", e);
        }
    }

    @Override
    public @NonNull TaskEventOutboxStats loadStats() {
        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(LOAD_STATS_SQL);
            ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                long pending = resultSet.getLong("pending");
                long deadLettered = resultSet.getLong("dead_lettered");
                return new TaskEventOutboxStats(pending, deadLettered);
            }
            return new TaskEventOutboxStats(0, 0);
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to load outbox stats", e);
        }
    }

    @Override
    public int purgePublishedEvents(
        @NonNull Instant olderThan
    ) {
        Objects.requireNonNull(olderThan, "olderThan");

        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(PURGE_PUBLISHED_SQL)) {
            statement.setTimestamp(1, toTimestamp(olderThan));
            statement.setTimestamp(2, toTimestamp(olderThan));
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to purge published outbox events", e);
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
