package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.kafka.KafkaTaskEventPublisher;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

final class KafkaTaskEventPublisherReporter implements SmartInitializingSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaTaskEventPublisherReporter.class);

    private final KafkaTaskEventPublisher publisher;
    private final KafkaTaskEventPublisherLifecycle lifecycle;
    private final TaskKafkaProperties properties;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    KafkaTaskEventPublisherReporter(
        KafkaTaskEventPublisher publisher,
        KafkaTaskEventPublisherLifecycle lifecycle,
        TaskKafkaProperties properties,
        ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.meterRegistryProvider = Objects.requireNonNull(meterRegistryProvider, "meterRegistryProvider");
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> bootstrapServers = properties.getBootstrapServers();
        Map<String, String> extraProperties = properties.getProducerProperties();
        String extraKeys = extraProperties == null || extraProperties.isEmpty()
            ? "[]"
            : extraProperties.keySet().stream().sorted().collect(Collectors.toList()).toString();

        LOGGER.info(
            "Kafka task event publisher configured: type={}, topic={}, batchSize={}, pollInterval={}, leaseDuration={}, "
                + "publishTimeout={}, failureBackoff={}, maxPublishAttempts={}, autoStart={}, threadNameFormat={}, "
                + "bootstrapServers={}, producerPropertiesKeys={}",
            publisher.getClass().getName(),
            properties.getTopic(),
            properties.getBatchSize(),
            properties.getPollInterval(),
            properties.getLeaseDuration(),
            properties.getPublishTimeout(),
            properties.getFailureBackoff(),
            properties.getMaxPublishAttempts(),
            properties.isAutoStart(),
            properties.getThreadNameFormat(),
            bootstrapServers,
            extraKeys
        );

        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }

        Gauge.builder("task.kafka.publisher.running", lifecycle, l -> l.isRunning() ? 1 : 0)
            .description("Whether Kafka publisher lifecycle is running (1 = true, 0 = false)")
            .register(registry);
        Gauge.builder("task.kafka.publisher.auto_start", properties, p -> p.isAutoStart() ? 1 : 0)
            .description("Whether Kafka publisher auto-start is enabled (1 = true, 0 = false)")
            .register(registry);
        Gauge.builder("task.kafka.publisher.batch_size", properties, TaskKafkaProperties::getBatchSize)
            .description("Configured Kafka publisher batch size")
            .register(registry);
        Gauge.builder("task.kafka.publisher.poll_interval_ms", properties, p -> p.getPollInterval().toMillis())
            .description("Configured Kafka publisher poll interval in milliseconds")
            .register(registry);
        Gauge.builder("task.kafka.publisher.lease_duration_ms", properties, p -> p.getLeaseDuration().toMillis())
            .description("Configured Kafka publisher lease duration in milliseconds")
            .register(registry);
        Gauge.builder("task.kafka.publisher.publish_timeout_ms", properties, p -> p.getPublishTimeout().toMillis())
            .description("Configured Kafka publisher publish timeout in milliseconds")
            .register(registry);
        Gauge.builder("task.kafka.publisher.failure_backoff_ms", properties, p -> p.getFailureBackoff().toMillis())
            .description("Configured Kafka publisher failure backoff in milliseconds")
            .register(registry);
        Gauge.builder("task.kafka.publisher.max_publish_attempts", properties, TaskKafkaProperties::getMaxPublishAttempts)
            .description("Configured Kafka publisher max publish attempts")
            .register(registry);
    }
}
