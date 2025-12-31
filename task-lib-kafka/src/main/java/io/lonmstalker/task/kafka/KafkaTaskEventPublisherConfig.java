package io.lonmstalker.task.kafka;

import java.time.Duration;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Configuration for Kafka task event publishing.
 */
public record KafkaTaskEventPublisherConfig(
    @NonNull String topic,
    @NonNull String leaseOwner,
    @NonNull Duration leaseDuration,
    int batchSize,
    @NonNull Duration publishTimeout,
    @NonNull Duration failureBackoff,
    int maxPublishAttempts
) {

    public static final Duration DEFAULT_FAILURE_BACKOFF = Duration.ofSeconds(5);
    public static final int DEFAULT_MAX_PUBLISH_ATTEMPTS = 10;

    public KafkaTaskEventPublisherConfig(
        @NonNull String topic,
        @NonNull String leaseOwner,
        @NonNull Duration leaseDuration,
        int batchSize,
        @NonNull Duration publishTimeout
    ) {
        this(topic, leaseOwner, leaseDuration, batchSize, publishTimeout, DEFAULT_FAILURE_BACKOFF, DEFAULT_MAX_PUBLISH_ATTEMPTS);
    }

    public KafkaTaskEventPublisherConfig {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(leaseOwner, "leaseOwner");
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        Objects.requireNonNull(publishTimeout, "publishTimeout");
        Objects.requireNonNull(failureBackoff, "failureBackoff");
        if (topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner must not be blank");
        }
        if (leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be >= 0");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
        if (publishTimeout.isNegative()) {
            throw new IllegalArgumentException("publishTimeout must be >= 0");
        }
        if (failureBackoff.isNegative()) {
            throw new IllegalArgumentException("failureBackoff must be >= 0");
        }
        if (maxPublishAttempts < 0) {
            throw new IllegalArgumentException("maxPublishAttempts must be >= 0");
        }
    }
}
