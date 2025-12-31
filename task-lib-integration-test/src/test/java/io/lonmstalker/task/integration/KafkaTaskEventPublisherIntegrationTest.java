package io.lonmstalker.task.integration;

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
import io.lonmstalker.task.impl.store.postgres.PostgresTaskStore;
import io.lonmstalker.task.kafka.JacksonTaskEventMessageCodec;
import io.lonmstalker.task.kafka.KafkaTaskEventPublisher;
import io.lonmstalker.task.kafka.KafkaTaskEventPublisherConfig;
import io.lonmstalker.task.kafka.TaskEventEnvelope;
import io.lonmstalker.task.kafka.TaskEventMessageCodec;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxClaim;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStore;
import io.lonmstalker.task.kafka.outbox.postgres.PostgresTaskEventOutboxStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kafka outbox publisher integration")
class KafkaTaskEventPublisherIntegrationTest extends KafkaPostgresIntegrationTestBase {

    private static final TaskType TASK_TYPE = TaskType.of("kafka");
    private static final TaskState TASK_STATE = TaskState.of("NEW");

    @Test
    @DisplayName("shouldPublishEventWithContexts")
    void shouldPublishEventWithContexts() {
        String topic = "task-events-" + UUID.randomUUID();
        KafkaTestSupport.createTopic(KAFKA.getBootstrapServers(), topic);

        Instant now = Instant.now();
        TaskRecord task = record(TaskKey.of("task-" + UUID.randomUUID()), now);
        TaskPayload requestPayload = new TaskPayload("request".getBytes(StandardCharsets.UTF_8), "text/plain");
        TaskPayload chainPayload = new TaskPayload("chain".getBytes(StandardCharsets.UTF_8), "text/plain");
        TaskEventContextEntry chainContext = new TaskEventContextEntry(
            task.id(),
            task.type(),
            TaskEventContextKind.CHAIN,
            chainPayload,
            now
        );

        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        store.create(task, List.of());
        store.recordRequestContext(task.id(), task.type(), requestPayload, now);

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
            "publisher",
            Duration.ofSeconds(30),
            10,
            Duration.ofSeconds(5)
        );

        try (KafkaProducer<String, byte[]> producer = KafkaTestSupport.createProducer(KAFKA.getBootstrapServers());
            KafkaTaskEventPublisher publisher = new KafkaTaskEventPublisher(outboxStore, producer, config);
            KafkaConsumer<String, byte[]> consumer =
                KafkaTestSupport.createConsumer(KAFKA.getBootstrapServers(), "group-" + UUID.randomUUID())) {
            consumer.subscribe(List.of(topic));

            int published = publisher.publishOnce();
            assertThat(published).isEqualTo(1);
            assertThat(publisher.publishOnce()).isZero();

            List<ConsumerRecord<String, byte[]>> records = pollRecords(consumer, Duration.ofSeconds(5));
            assertThat(records).hasSize(1);

            TaskEventEnvelope envelope = new JacksonTaskEventMessageCodec().deserialize(records.get(0).value());
            assertThat(envelope.event().id()).isEqualTo(event.id());
            assertThat(envelope.contexts())
                .extracting(TaskEventContextEntry::kind)
                .contains(TaskEventContextKind.REQUEST, TaskEventContextKind.CHAIN);
        }
    }

    @Test
    @DisplayName("shouldPublishBatch")
    void shouldPublishBatch() {
        String topic = "task-events-" + UUID.randomUUID();
        KafkaTestSupport.createTopic(KAFKA.getBootstrapServers(), topic);

        Instant now = Instant.now();
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskEventOutboxStore outboxStore = new PostgresTaskEventOutboxStore(dataSource);

        for (int i = 0; i < 3; i++) {
            TaskRecord task = record(TaskKey.of("task-" + UUID.randomUUID()), now);
            store.create(task, List.of());
            TaskEventRecord event = new TaskEventRecord(
                TaskEventId.random(),
                task.id(),
                task.key(),
                task.type(),
                TaskEventType.CHAIN_COMPLETED,
                now
            );
            store.createEvent(event, List.of(), List.of(task.id()));
        }

        KafkaTaskEventPublisherConfig config = new KafkaTaskEventPublisherConfig(
            topic,
            "publisher",
            Duration.ofSeconds(30),
            10,
            Duration.ofSeconds(5)
        );

        try (KafkaProducer<String, byte[]> producer = KafkaTestSupport.createProducer(KAFKA.getBootstrapServers());
            KafkaTaskEventPublisher publisher = new KafkaTaskEventPublisher(outboxStore, producer, config);
            KafkaConsumer<String, byte[]> consumer =
                KafkaTestSupport.createConsumer(KAFKA.getBootstrapServers(), "group-" + UUID.randomUUID())) {
            consumer.subscribe(List.of(topic));

            int published = publisher.publishOnce();
            assertThat(published).isEqualTo(3);

            List<ConsumerRecord<String, byte[]>> records = pollRecords(consumer, Duration.ofSeconds(5));
            assertThat(records).hasSize(3);
        }
    }

    @Test
    @DisplayName("shouldReleaseLeaseOnSerializationFailure")
    void shouldReleaseLeaseOnSerializationFailure() {
        String topic = "task-events-" + UUID.randomUUID();
        KafkaTestSupport.createTopic(KAFKA.getBootstrapServers(), topic);

        Instant now = Instant.now();
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskRecord task = record(TaskKey.of("task-" + UUID.randomUUID()), now);
        store.create(task, List.of());

        TaskEventRecord event = new TaskEventRecord(
            TaskEventId.random(),
            task.id(),
            task.key(),
            task.type(),
            TaskEventType.CHAIN_COMPLETED,
            now
        );
        store.createEvent(event, List.of(), List.of(task.id()));

        TaskEventMessageCodec failingCodec = new TaskEventMessageCodec() {
            @Override
            public byte[] serialize(TaskEventEnvelope envelope) {
                throw new IllegalStateException("boom");
            }

            @Override
            public TaskEventEnvelope deserialize(byte[] payload) {
                throw new IllegalStateException("boom");
            }
        };

        TaskEventOutboxStore outboxStore = new PostgresTaskEventOutboxStore(dataSource);
        KafkaTaskEventPublisherConfig config = new KafkaTaskEventPublisherConfig(
            topic,
            "publisher",
            Duration.ofSeconds(30),
            10,
            Duration.ofSeconds(5)
        );

        try (KafkaProducer<String, byte[]> producer = KafkaTestSupport.createProducer(KAFKA.getBootstrapServers());
            KafkaTaskEventPublisher publisher =
                new KafkaTaskEventPublisher(outboxStore, producer, config, failingCodec, record -> null, java.time.Clock.systemUTC())) {
            int published = publisher.publishOnce();
            assertThat(published).isZero();
        }

        TaskEventOutboxClaim retry = new TaskEventOutboxClaim(Instant.now(), Duration.ofSeconds(30), 10, "retry");
        List<TaskEventRecord> claimed = outboxStore.claim(retry);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).id()).isEqualTo(event.id());
    }

    private static TaskRecord record(
        TaskKey key,
        Instant now
    ) {
        TaskPayload payload = new TaskPayload("payload".getBytes(StandardCharsets.UTF_8), "text/plain");
        return new TaskRecord(
            TaskId.random(),
            key,
            TASK_TYPE,
            TASK_STATE,
            TaskStatus.PENDING,
            0,
            1,
            null,
            null,
            null,
            payload,
            now,
            now,
            null
        );
    }

    private static List<ConsumerRecord<String, byte[]>> pollRecords(
        KafkaConsumer<String, byte[]> consumer,
        Duration timeout
    ) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
        while (System.nanoTime() < deadline && records.isEmpty()) {
            ConsumerRecords<String, byte[]> polled = consumer.poll(Duration.ofMillis(200));
            polled.forEach(records::add);
        }
        return records;
    }
}
