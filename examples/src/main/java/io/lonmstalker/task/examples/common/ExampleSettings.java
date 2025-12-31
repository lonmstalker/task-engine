package io.lonmstalker.task.examples.common;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

public final class ExampleSettings {

    public static final String DB_URL = "TASK_EXAMPLE_DB_URL";
    public static final String DB_USER = "TASK_EXAMPLE_DB_USER";
    public static final String DB_PASSWORD = "TASK_EXAMPLE_DB_PASSWORD";
    public static final String KAFKA_BOOTSTRAP = "TASK_EXAMPLE_KAFKA_BOOTSTRAP";
    public static final String KAFKA_TOPIC = "TASK_EXAMPLE_KAFKA_TOPIC";

    private ExampleSettings() {
    }

    public static DataSource createDataSource() {
        String url = requireEnv(DB_URL);
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(url);

        String user = getenv(DB_USER);
        if (user != null && !user.isBlank()) {
            dataSource.setUser(user);
        }

        String password = getenv(DB_PASSWORD);
        if (password != null && !password.isBlank()) {
            dataSource.setPassword(password);
        }

        return dataSource;
    }

    public static String requireEnv(String name) {
        String value = getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    public static String getenv(String name) {
        return System.getenv(name);
    }
}
