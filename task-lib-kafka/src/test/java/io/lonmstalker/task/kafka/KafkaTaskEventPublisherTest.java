package io.lonmstalker.task.kafka;

import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.event.TaskEventType;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxAdminStore;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxClaim;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTaskEventPublisherTest {

    @Test
    void shouldAttemptPublishBeforeDeadLetterWhenMaxAttemptsIsOne() {
        InMemoryOutboxStore outboxStore = new InMemoryOutboxStore();
        TaskEventRecord record = new TaskEventRecord(
            TaskEventId.random(),
            TaskId.random(),
            TaskKey.of("task-1"),
            TaskType.of("type-1"),
            TaskEventType.CHAIN_COMPLETED,
            Instant.now()
        );
        outboxStore.add(record);

        MockProducer<String, byte[]> producer = new MockProducer<>(
            true,
            new StringSerializer(),
            new ByteArraySerializer()
        );
        TrackingFailingCodec codec = new TrackingFailingCodec();
        KafkaTaskEventPublisherConfig config = new KafkaTaskEventPublisherConfig(
            "task-events",
            "publisher-1",
            Duration.ofSeconds(10),
            1,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            1
        );

        try (KafkaTaskEventPublisher publisher = new KafkaTaskEventPublisher(
            outboxStore,
            producer,
            config,
            codec,
            TaskEventKeyProvider.taskId(),
            Clock.systemUTC()
        )) {
            int published = publisher.publishOnce();

            assertThat(published).isZero();
            assertThat(codec.serializeCalls()).isEqualTo(1);
            assertThat(outboxStore.deadLetteredIds()).contains(record.id());
            assertThat(outboxStore.loadPublishAttempts(record.id())).isEqualTo(1);
        }
    }

    private static final class TrackingFailingCodec implements TaskEventMessageCodec {
        private int serializeCalls;

        int serializeCalls() {
            return serializeCalls;
        }

        @Override
        public byte[] serialize(
            TaskEventEnvelope envelope
        ) {
            Objects.requireNonNull(envelope, "envelope");
            serializeCalls++;
            throw new IllegalStateException("serialization failed");
        }

        @Override
        public TaskEventEnvelope deserialize(
            byte[] payload
        ) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static final class InMemoryOutboxStore implements TaskEventOutboxStore, TaskEventOutboxAdminStore {
        private final Map<TaskEventId, Entry> entries = new HashMap<>();

        void add(
            TaskEventRecord record
        ) {
            Objects.requireNonNull(record, "record");
            entries.put(record.id(), new Entry(record));
        }

        Set<TaskEventId> deadLetteredIds() {
            return entries.values().stream()
                .filter(entry -> entry.deadLettered)
                .map(entry -> entry.record.id())
                .collect(Collectors.toSet());
        }

        @Override
        public List<TaskEventRecord> claim(
            TaskEventOutboxClaim claim
        ) {
            Objects.requireNonNull(claim, "claim");
            List<TaskEventRecord> claimed = new ArrayList<>();
            for (Entry entry : entries.values()) {
                if (entry.deadLettered || entry.published) {
                    continue;
                }
                entry.attempts++;
                claimed.add(entry.record);
                if (claimed.size() >= claim.maxEvents()) {
                    break;
                }
            }
            return List.copyOf(claimed);
        }

        @Override
        public List<TaskEventContextEntry> loadContexts(
            TaskEventId eventId
        ) {
            Objects.requireNonNull(eventId, "eventId");
            return List.of();
        }

        @Override
        public void markPublished(
            TaskEventId eventId,
            String leaseOwner,
            Instant publishedAt
        ) {
            Entry entry = entry(eventId);
            entry.published = true;
            entry.publishedAt = publishedAt;
        }

        @Override
        public void release(
            TaskEventId eventId,
            String leaseOwner,
            Instant releasedAt
        ) {
            entry(eventId).releasedAt = releasedAt;
        }

        @Override
        public int loadPublishAttempts(
            TaskEventId eventId
        ) {
            return entry(eventId).attempts;
        }

        @Override
        public void markDeadLetter(
            TaskEventId eventId,
            String leaseOwner,
            Instant deadLetterAt,
            String reason
        ) {
            Entry entry = entry(eventId);
            entry.deadLettered = true;
            entry.deadLetterReason = reason;
            entry.deadLetterAt = deadLetterAt;
        }

        @Override
        public void close() {
        }

        private Entry entry(
            TaskEventId eventId
        ) {
            Objects.requireNonNull(eventId, "eventId");
            Entry entry = entries.get(eventId);
            if (entry == null) {
                throw new IllegalArgumentException("Unknown event: " + eventId.value());
            }
            return entry;
        }

        private static final class Entry {
            private final TaskEventRecord record;
            private int attempts;
            private boolean published;
            private boolean deadLettered;
            private Instant publishedAt;
            private Instant releasedAt;
            private Instant deadLetterAt;
            private String deadLetterReason;

            private Entry(
                TaskEventRecord record
            ) {
                this.record = record;
            }
        }
    }
}
