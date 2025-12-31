package io.lonmstalker.task.spring.autoconfigure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.lonmstalker.task.kafka.KafkaTaskEventPublisher;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStore;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TaskKafkaAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskKafkaAutoConfiguration.class));

    @Test
    void backsOffWhenDisabled() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .run(context -> {
                assertThat(context).doesNotHaveBean(KafkaTaskEventPublisher.class);
                assertThat(context).doesNotHaveBean(TaskEventOutboxStore.class);
            });
    }

    @Test
    void createsPublisherAndLifecycle() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .withPropertyValues(
                "task.kafka.enabled=true",
                "task.kafka.auto-start=false",
                "task.kafka.topic=task-events",
                "task.kafka.bootstrap-servers=localhost:9092"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(KafkaTaskEventPublisher.class);
                assertThat(context).hasSingleBean(KafkaTaskEventPublisherLifecycle.class);
                assertThat(context).hasSingleBean(KafkaProducer.class);

                KafkaTaskEventPublisherLifecycle lifecycle = context.getBean(KafkaTaskEventPublisherLifecycle.class);
                assertThat(lifecycle.isAutoStartup()).isFalse();
            });
    }

    @Test
    void registersPublisherMetrics() {
        contextRunner
            .withUserConfiguration(BaseConfig.class, MetricsConfig.class)
            .withPropertyValues(
                "task.kafka.enabled=true",
                "task.kafka.auto-start=false",
                "task.kafka.topic=task-events",
                "task.kafka.bootstrap-servers=localhost:9092"
            )
            .run(context -> {
                MeterRegistry registry = context.getBean(MeterRegistry.class);
                Gauge running = registry.find("task.kafka.publisher.running").gauge();
                Gauge batchSize = registry.find("task.kafka.publisher.batch_size").gauge();
                Gauge pollInterval = registry.find("task.kafka.publisher.poll_interval_ms").gauge();

                assertThat(running).isNotNull();
                assertThat(batchSize).isNotNull();
                assertThat(pollInterval).isNotNull();
                assertThat(running.value()).isEqualTo(0.0);
                assertThat(batchSize.value()).isEqualTo(100.0);
                assertThat(pollInterval.value()).isEqualTo(1000.0);
            });
    }

    @Configuration
    static class BaseConfig {

        @Bean
        DataSource dataSource() {
            return new StubDataSource();
        }
    }

    @Configuration
    static class MetricsConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    static final class StubDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new UnsupportedOperationException("No connection");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new UnsupportedOperationException("No connection");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger("stub");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new UnsupportedOperationException("unwrap");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
