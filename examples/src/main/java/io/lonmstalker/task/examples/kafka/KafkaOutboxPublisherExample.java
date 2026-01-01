package io.lonmstalker.task.examples.kafka;

import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventContextKind;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.event.TaskEventType;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.examples.common.ExampleSettings;
import io.lonmstalker.task.impl.store.postgres.PostgresTaskStore;
import io.lonmstalker.task.kafka.JacksonTaskEventMessageCodec;
import io.lonmstalker.task.kafka.KafkaTaskEventPublisher;
import io.lonmstalker.task.kafka.KafkaTaskEventPublisherConfig;
import io.lonmstalker.task.kafka.TaskEventEnvelope;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStore;
import io.lonmstalker.task.kafka.outbox.postgres.PostgresTaskEventOutboxStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaOutboxPublisherExample {

    private static final TaskState STATE_DONE = TaskState.of("DONE");

    private KafkaOutboxPublisherExample() {
    }

    public static void main(String[] args) throws Exception {
        DataSource dataSource = ExampleSettings.createDataSource();
        String bootstrapServers = ExampleSettings.requireEnv(ExampleSettings.KAFKA_BOOTSTRAP);
        String topic = ExampleSettings.requireEnv(ExampleSettings.KAFKA_TOPIC);

        Instant now = Instant.now();
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskRecord task = new TaskRecord(
            TaskId.random(),
            TaskKey.of("kafka-" + UUID.randomUUID()),
            TaskType.of("kafka"),
            STATE_DONE,
            TaskStatus.COMPLETED,
            null,
            1,
            1,
            null,
            null,
            null,
            new TaskPayload("payload".getBytes(StandardCharsets.UTF_8), "text/plain"),
            now,
            now,
            null
        );

        store.create(task, List.of());

        TaskPayload requestPayload = new TaskPayload("request".getBytes(StandardCharsets.UTF_8), "text/plain");
        store.recordRequestContext(task.id(), task.type(), requestPayload, now);

        TaskPayload chainPayload = new TaskPayload("chain".getBytes(StandardCharsets.UTF_8), "text/plain");
        TaskEventContextEntry chainContext = new TaskEventContextEntry(
            task.id(),
            task.type(),
            TaskEventContextKind.CHAIN,
            chainPayload,
            now
        );

        TaskEventRecord event = new TaskEventRecord(
            TaskEventId.random(),
            task.id(),
            task.key(),
            task.type(),
            TaskEventType.CHAIN_COMPLETED,
            now
        );

        store.createEvent(event, List.of(chainContext), List.of(task.id()));

        TaskEventOutboxStore outboxStore = new PostgresTaskEventOutboxStore(dataSource);
        KafkaTaskEventPublisherConfig config = new KafkaTaskEventPublisherConfig(
            topic,
            "example-publisher",
            Duration.ofSeconds(30),
            10,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            10
        );

        try (KafkaProducer<String, byte[]> producer = createProducer(bootstrapServers);
            KafkaConsumer<String, byte[]> consumer = createConsumer(bootstrapServers);
            KafkaTaskEventPublisher publisher = new KafkaTaskEventPublisher(outboxStore, producer, config)) {

            consumer.subscribe(List.of(topic));

            int published = publisher.publishOnce();
            System.out.println("Published events: " + published);

            List<ConsumerRecord<String, byte[]>> records = pollRecords(consumer, Duration.ofSeconds(5));
            if (records.isEmpty()) {
                System.out.println("No Kafka records received.");
                return;
            }

            TaskEventEnvelope envelope = new JacksonTaskEventMessageCodec().deserialize(records.get(0).value());
            System.out.println("Kafka event: " + envelope.event().id().value());
            System.out.println("Contexts: " + envelope.contexts().size());
        }
    }

    private static KafkaProducer<String, byte[]> createProducer(
        String bootstrapServers
    ) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return new KafkaProducer<>(properties);
    }

    private static KafkaConsumer<String, byte[]> createConsumer(
        String bootstrapServers
    ) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "task-example-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }

    private static List<ConsumerRecord<String, byte[]>> pollRecords(
        KafkaConsumer<String, byte[]> consumer,
        Duration timeout
    ) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<ConsumerRecord<String, byte[]>> results = new ArrayList<>();

        while (System.nanoTime() < deadline && results.isEmpty()) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(results::add);
        }

        return results;
    }
}
