package io.lonmstalker.task.kafka;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Serializer for task event Kafka messages.
 */
public interface TaskEventMessageCodec {

    @NonNull byte[] serialize(
        @NonNull TaskEventEnvelope envelope
    );

    @NonNull TaskEventEnvelope deserialize(
        @NonNull byte[] payload
    );
}
