package io.lonmstalker.task.impl.store.postgres;

import io.lonmstalker.task.api.error.TaskStoreException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

final class PostgresTransactionManager {

    @FunctionalInterface
    interface SqlFunction<T> {
        T apply(
            @NonNull Connection connection
        ) throws SQLException;
    }

    private final @NonNull DataSource dataSource;
    private final @NonNull ThreadLocal<Connection> transactionalConnection = new ThreadLocal<>();

    PostgresTransactionManager(
        @NonNull DataSource dataSource
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Nullable Connection current() {
        return transactionalConnection.get();
    }

    <T> T withConnection(
        @NonNull SqlFunction<T> action
    ) throws SQLException {
        Connection existing = transactionalConnection.get();
        if (existing != null) {
            return action.apply(existing);
        }

        try (Connection connection = dataSource.getConnection()) {
            return action.apply(connection);
        }
    }

    <T> T inLocalTransaction(
        @NonNull SqlFunction<T> action
    ) throws SQLException {
        Connection existing = transactionalConnection.get();
        if (existing != null) {
            return action.apply(existing);
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                T result = action.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } catch (RuntimeException | Error e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    throw new TaskStoreException("Failed to rollback transaction", rollbackError);
                }
                throw e;
            }
        }
    }

    <T> T inTransaction(
        @NonNull Supplier<T> action
    ) {
        Objects.requireNonNull(action, "action");

        if (transactionalConnection.get() != null) {
            return action.get();
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            transactionalConnection.set(connection);

            try {
                T result = action.get();
                connection.commit();
                return result;
            } catch (RuntimeException | Error e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    throw new TaskStoreException("Failed to rollback transaction", rollbackError);
                }
                throw e;
            } finally {
                transactionalConnection.remove();
            }
        } catch (SQLException e) {
            throw new TaskStoreException("Failed to execute transaction", e);
        }
    }
}
