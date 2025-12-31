package io.lonmstalker.task.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

public final class PostgresTestSupport {

    private static final String SCHEMA_RESOURCE = "io/lonmstalker/task/impl/store/postgres/schema.sql";

    private PostgresTestSupport() {
    }

    public static DataSource createDataSource(
        String jdbcUrl,
        String username,
        String password
    ) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(jdbcUrl);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    public static void applySchema(
        DataSource dataSource
    ) {
        executeSql(dataSource, loadResource(SCHEMA_RESOURCE));
    }

    public static void truncateAll(
        DataSource dataSource
    ) {
        executeSql(dataSource, "TRUNCATE task_event_contexts, task_event_outbox, task_links, task_tasks CASCADE");
    }

    public static void truncateEvents(
        DataSource dataSource
    ) {
        executeSql(dataSource, "TRUNCATE task_event_contexts, task_event_outbox CASCADE");
    }

    private static void executeSql(
        DataSource dataSource,
        String sql
    ) {
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            for (String entry : sql.split(";")) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                statement.execute(trimmed);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to execute SQL", e);
        }
    }

    private static String loadResource(
        String path
    ) {
        try (InputStream input = PostgresTestSupport.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }
}
