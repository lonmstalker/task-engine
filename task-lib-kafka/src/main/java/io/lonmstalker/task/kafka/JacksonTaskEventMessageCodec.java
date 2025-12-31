package io.lonmstalker.task.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Jackson-based serializer for task event messages.
 */
public final class JacksonTaskEventMessageCodec implements TaskEventMessageCodec {

    private final @NonNull ObjectMapper mapper;

    public JacksonTaskEventMessageCodec() {
        this(defaultMapper());
    }

    public JacksonTaskEventMessageCodec(
        @NonNull ObjectMapper mapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public @NonNull byte[] serialize(
        @NonNull TaskEventEnvelope envelope
    ) {
        Objects.requireNonNull(envelope, "envelope");
        try {
            return mapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize task event envelope", e);
        }
    }

    @Override
    public @NonNull TaskEventEnvelope deserialize(
        @NonNull byte[] payload
    ) {
        Objects.requireNonNull(payload, "payload");
        try {
            return mapper.readValue(payload, TaskEventEnvelope.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize task event envelope", e);
        }
    }

    private static @NonNull ObjectMapper defaultMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
