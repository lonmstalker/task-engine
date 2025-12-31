package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.kafka.JacksonTaskEventMessageCodec;
import io.lonmstalker.task.kafka.KafkaTaskEventPublisher;
import io.lonmstalker.task.kafka.KafkaTaskEventPublisherConfig;
import io.lonmstalker.task.kafka.TaskEventKeyProvider;
import io.lonmstalker.task.kafka.TaskEventMessageCodec;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStore;
import io.lonmstalker.task.kafka.outbox.postgres.PostgresTaskEventOutboxStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(KafkaTaskEventPublisher.class)
@EnableConfigurationProperties(TaskKafkaProperties.class)
@ConditionalOnProperty(prefix = "task.kafka", name = "enabled", havingValue = "true")
public class TaskKafkaAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public TaskEventOutboxStore taskEventOutboxStore(
        DataSource dataSource
    ) {
        return new PostgresTaskEventOutboxStore(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaProducer<String, byte[]> kafkaTaskEventProducer(
        TaskKafkaProperties properties
    ) {
        List<String> bootstrapServers = properties.getBootstrapServers();
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            throw new IllegalStateException("task.kafka.bootstrap-servers must be set");
        }

        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", bootstrapServers));
        Map<String, String> extraProperties = properties.getProducerProperties();
        if (extraProperties != null && !extraProperties.isEmpty()) {
            producerProperties.putAll(extraProperties);
        }
        producerProperties.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return new KafkaProducer<>(producerProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskEventMessageCodec taskEventMessageCodec(
        ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapperProvider
    ) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            return new JacksonTaskEventMessageCodec(mapper);
        }
        return new JacksonTaskEventMessageCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskEventKeyProvider taskEventKeyProvider() {
        return TaskEventKeyProvider.taskId();
    }

    @Bean(destroyMethod = "")
    @ConditionalOnBean(TaskEventOutboxStore.class)
    @ConditionalOnMissingBean
    public KafkaTaskEventPublisher kafkaTaskEventPublisher(
        TaskEventOutboxStore outboxStore,
        KafkaProducer<String, byte[]> producer,
        TaskKafkaProperties properties,
        TaskEventMessageCodec messageCodec,
        TaskEventKeyProvider keyProvider,
        ObjectProvider<Clock> clockProvider
    ) {
        String topic = properties.getTopic();
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException("task.kafka.topic must be set");
        }

        String leaseOwner = resolveLeaseOwner(properties.getLeaseOwner());
        KafkaTaskEventPublisherConfig config = new KafkaTaskEventPublisherConfig(
            topic,
            leaseOwner,
            properties.getLeaseDuration(),
            properties.getBatchSize(),
            properties.getPublishTimeout()
        );

        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);
        return new KafkaTaskEventPublisher(outboxStore, producer, config, messageCodec, keyProvider, clock);
    }

    @Bean
    @ConditionalOnBean(KafkaTaskEventPublisher.class)
    public KafkaTaskEventPublisherLifecycle kafkaTaskEventPublisherLifecycle(
        KafkaTaskEventPublisher publisher,
        TaskKafkaProperties properties
    ) {
        return new KafkaTaskEventPublisherLifecycle(
            publisher,
            properties.getPollInterval(),
            properties.isAutoStart(),
            resolveThreadNameFormat(properties.getThreadNameFormat())
        );
    }

    @Bean
    @ConditionalOnBean(KafkaTaskEventPublisher.class)
    public KafkaTaskEventPublisherReporter kafkaTaskEventPublisherReporter(
        KafkaTaskEventPublisher publisher,
        KafkaTaskEventPublisherLifecycle lifecycle,
        TaskKafkaProperties properties,
        ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        return new KafkaTaskEventPublisherReporter(publisher, lifecycle, properties, meterRegistryProvider);
    }

    private static String resolveLeaseOwner(
        @Nullable String leaseOwner
    ) {
        if (leaseOwner == null || leaseOwner.isBlank()) {
            return "kafka-publisher-" + UUID.randomUUID();
        }
        return leaseOwner;
    }

    private static String resolveThreadNameFormat(
        @Nullable String threadNameFormat
    ) {
        if (threadNameFormat == null || threadNameFormat.isBlank()) {
            return "task-kafka-publisher-%d";
        }
        return threadNameFormat;
    }
}
