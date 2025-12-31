package io.lonmstalker.task.kafka;

import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxClaim;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes task events from the outbox to Kafka.
 */
public final class KafkaTaskEventPublisher implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaTaskEventPublisher.class);
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration DEFAULT_PUBLISH_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final @NonNull TaskEventOutboxStore outboxStore;
    private final @NonNull KafkaProducer<String, byte[]> producer;
    private final @NonNull String topic;
    private final @NonNull String leaseOwner;
    private final @NonNull Duration leaseDuration;
    private final int batchSize;
    private final @NonNull Duration publishTimeout;
    private final @NonNull TaskEventMessageCodec messageCodec;
    private final @NonNull TaskEventKeyProvider keyProvider;
    private final @NonNull Clock clock;

    public KafkaTaskEventPublisher(
        @NonNull TaskEventOutboxStore outboxStore,
        @NonNull KafkaProducer<String, byte[]> producer,
        @NonNull String topic
    ) {
        this(
            outboxStore,
            producer,
            new KafkaTaskEventPublisherConfig(
                topic,
                "kafka-publisher-" + UUID.randomUUID(),
                DEFAULT_LEASE_DURATION,
                DEFAULT_BATCH_SIZE,
                DEFAULT_PUBLISH_TIMEOUT
            ),
            new JacksonTaskEventMessageCodec(),
            TaskEventKeyProvider.taskId(),
            Clock.systemUTC()
        );
    }

    public KafkaTaskEventPublisher(
        @NonNull TaskEventOutboxStore outboxStore,
        @NonNull KafkaProducer<String, byte[]> producer,
        @NonNull KafkaTaskEventPublisherConfig config
    ) {
        this(
            outboxStore,
            producer,
            config,
            new JacksonTaskEventMessageCodec(),
            TaskEventKeyProvider.taskId(),
            Clock.systemUTC()
        );
    }

    public KafkaTaskEventPublisher(
        @NonNull TaskEventOutboxStore outboxStore,
        @NonNull KafkaProducer<String, byte[]> producer,
        @NonNull KafkaTaskEventPublisherConfig config,
        @NonNull TaskEventMessageCodec messageCodec,
        @NonNull TaskEventKeyProvider keyProvider,
        @NonNull Clock clock
    ) {
        this.outboxStore = Objects.requireNonNull(outboxStore, "outboxStore");
        this.producer = Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(config, "config");
        this.topic = config.topic();
        this.leaseOwner = config.leaseOwner();
        this.leaseDuration = config.leaseDuration();
        this.batchSize = config.batchSize();
        this.publishTimeout = config.publishTimeout();
        this.messageCodec = Objects.requireNonNull(messageCodec, "messageCodec");
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int publishOnce() {
        Instant now = clock.instant();
        TaskEventOutboxClaim claim = new TaskEventOutboxClaim(now, leaseDuration, batchSize, leaseOwner);
        List<TaskEventRecord> records = outboxStore.claim(claim);
        if (records.isEmpty()) {
            return 0;
        }

        List<PendingPublish> pending = new ArrayList<>(records.size());
        for (TaskEventRecord record : records) {
            try {
                List<TaskEventContextEntry> contexts = outboxStore.loadContexts(record.id());
                TaskEventEnvelope envelope = new TaskEventEnvelope(record, contexts);
                byte[] payload = messageCodec.serialize(envelope);
                String key = keyProvider.keyFor(record);
                ProducerRecord<String, byte[]> message = new ProducerRecord<>(topic, key, payload);
                Future<RecordMetadata> future = producer.send(message);
                pending.add(new PendingPublish(record.id(), future));
            } catch (RuntimeException e) {
                release(record.id(), e);
            }
        }

        if (pending.isEmpty()) {
            return 0;
        }

        try {
            producer.flush();
        } catch (RuntimeException e) {
            for (PendingPublish entry : pending) {
                release(entry.eventId(), e);
            }
            throw e;
        }

        Instant publishedAt = clock.instant();
        int published = 0;
        for (PendingPublish entry : pending) {
            if (awaitPublish(entry)) {
                outboxStore.markPublished(entry.eventId(), leaseOwner, publishedAt);
                published++;
            }
        }

        return published;
    }

    @Override
    public void close() {
        producer.close();
    }

    private boolean awaitPublish(
        @NonNull PendingPublish pending
    ) {
        try {
            pending.future().get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            release(pending.eventId(), e);
        } catch (ExecutionException | TimeoutException e) {
            release(pending.eventId(), e);
        }
        return false;
    }

    private void release(
        @NonNull TaskEventId eventId,
        @NonNull Exception error
    ) {
        LOGGER.warn("Failed to publish task event {}", eventId.value(), error);
        outboxStore.release(eventId, leaseOwner, clock.instant());
    }

    private record PendingPublish(
        @NonNull TaskEventId eventId,
        @NonNull Future<RecordMetadata> future
    ) {

        private PendingPublish {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(future, "future");
        }
    }
}
