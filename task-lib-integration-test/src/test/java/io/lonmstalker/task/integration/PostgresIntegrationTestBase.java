package io.lonmstalker.task.integration;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
abstract class PostgresIntegrationTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static DataSource dataSource;

    @BeforeAll
    static void initDataSource() {
        dataSource = PostgresTestSupport.createDataSource(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        PostgresTestSupport.applySchema(dataSource);
    }

    @BeforeEach
    void resetDatabase() {
        PostgresTestSupport.truncateAll(dataSource);
    }
}
