package io.lonmstalker.task.kafka;

import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxAdminStore;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxBatchStore;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxClaim;
import io.lonmstalker.task.kafka.outbox.TaskEventOutboxStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final @NonNull Duration failureBackoff;
    private final int maxPublishAttempts;
    private final @NonNull TaskEventMessageCodec messageCodec;
    private final @NonNull TaskEventKeyProvider keyProvider;
    private final @NonNull Clock clock;
    private final @NonNull TaskEventOutboxAdminStore adminStore;
    private final @NonNull TaskEventOutboxBatchStore batchStore;

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
                DEFAULT_PUBLISH_TIMEOUT,
                KafkaTaskEventPublisherConfig.DEFAULT_FAILURE_BACKOFF,
                KafkaTaskEventPublisherConfig.DEFAULT_MAX_PUBLISH_ATTEMPTS
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
        this.failureBackoff = config.failureBackoff();
        this.maxPublishAttempts = config.maxPublishAttempts();
        this.messageCodec = Objects.requireNonNull(messageCodec, "messageCodec");
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.adminStore = outboxStore instanceof TaskEventOutboxAdminStore admin
            ? admin
            : new NoopAdminStore();
        this.batchStore = outboxStore instanceof TaskEventOutboxBatchStore batch
            ? batch
            : new NoopBatchStore();

        if (maxPublishAttempts > 0 && !(outboxStore instanceof TaskEventOutboxAdminStore)) {
            LOGGER.warn("maxPublishAttempts is configured but outbox store does not support attempt tracking");
        }
    }

    public int publishOnce() {
        Instant now = clock.instant();
        TaskEventOutboxClaim claim = new TaskEventOutboxClaim(now, leaseDuration, batchSize, leaseOwner);
        List<TaskEventRecord> records = outboxStore.claim(claim);
        if (records.isEmpty()) {
            return 0;
        }

        Map<TaskEventId, List<TaskEventContextEntry>> batchedContexts = batchStore.loadContexts(
            records.stream().map(TaskEventRecord::id).toList()
        );

        List<PendingPublish> pending = new ArrayList<>(records.size());
        for (TaskEventRecord record : records) {
            try {
                if (shouldDeadLetter(record.id())) {
                    continue;
                }
                List<TaskEventContextEntry> contexts = batchedContexts.get(record.id());
                if (contexts == null) {
                    contexts = outboxStore.loadContexts(record.id());
                }
                TaskEventEnvelope envelope = new TaskEventEnvelope(record, contexts);
                byte[] payload = messageCodec.serialize(envelope);
                String key = keyProvider.keyFor(record);
                ProducerRecord<String, byte[]> message = new ProducerRecord<>(topic, key, payload);
                Future<RecordMetadata> future = producer.send(message);
                pending.add(new PendingPublish(record.id(), future));
            } catch (RuntimeException e) {
                handlePublishFailure(record.id(), e);
            }
        }

        if (pending.isEmpty()) {
            return 0;
        }

        try {
            producer.flush();
        } catch (RuntimeException e) {
            for (PendingPublish entry : pending) {
                handlePublishFailure(entry.eventId(), e);
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
            handlePublishFailure(pending.eventId(), e);
        } catch (ExecutionException | TimeoutException e) {
            handlePublishFailure(pending.eventId(), e);
        }
        return false;
    }

    private void handlePublishFailure(
        @NonNull TaskEventId eventId,
        @NonNull Exception error
    ) {
        LOGGER.warn("Failed to publish task event {}", eventId.value(), error);

        if (maxPublishAttempts > 0) {
            int attempts = adminStore.loadPublishAttempts(eventId);
            if (attempts >= maxPublishAttempts) {
                adminStore.markDeadLetter(
                    eventId,
                    leaseOwner,
                    clock.instant(),
                    "Exceeded max publish attempts: " + attempts + "; lastError=" + sanitizeError(error)
                );
                return;
            }
        }

        Instant releaseAt = clock.instant().plus(failureBackoff);
        outboxStore.release(eventId, leaseOwner, releaseAt);
    }

    private boolean shouldDeadLetter(
        @NonNull TaskEventId eventId
    ) {
        if (maxPublishAttempts <= 0) {
            return false;
        }

        int attempts = adminStore.loadPublishAttempts(eventId);
        if (attempts <= maxPublishAttempts) {
            return false;
        }

        adminStore.markDeadLetter(
            eventId,
            leaseOwner,
            clock.instant(),
            "Exceeded max publish attempts: " + attempts
        );
        return true;
    }

    private String sanitizeError(
        @NonNull Exception error
    ) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        String sanitized = message.replaceAll("[\r\n]+", " ").trim();
        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 500);
        }
        return error.getClass().getSimpleName() + ": " + sanitized;
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

    private static final class NoopAdminStore implements TaskEventOutboxAdminStore {

        @Override
        public int loadPublishAttempts(
            @NonNull TaskEventId eventId
        ) {
            return 0;
        }

        @Override
        public void markDeadLetter(
            @NonNull TaskEventId eventId,
            @NonNull String leaseOwner,
            @NonNull Instant deadLetterAt,
            @NonNull String reason
        ) {
            LOGGER.warn("Dead-letter requested but outbox store does not support it for {}", eventId.value());
        }
    }

    private static final class NoopBatchStore implements TaskEventOutboxBatchStore {

        @Override
        public @NonNull Map<TaskEventId, List<TaskEventContextEntry>> loadContexts(
            @NonNull List<TaskEventId> eventIds
        ) {
            return Map.of();
        }
    }
}
