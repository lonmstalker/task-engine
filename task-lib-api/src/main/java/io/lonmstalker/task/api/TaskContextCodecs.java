package io.lonmstalker.task.api;

import com.google.common.base.Preconditions;
import io.lonmstalker.task.api.model.TaskPayload;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Common context codecs.
 */
public final class TaskContextCodecs {

    private static final @NonNull TaskContextCodec<String> UTF8_STRING = new Utf8StringCodec();

    private TaskContextCodecs() {
    }

    /**
     * Returns UTF-8 string codec.
     *
     * @return string codec
     */
    public static @NonNull TaskContextCodec<String> stringUtf8() {
        return UTF8_STRING;
    }

    /**
     * Returns raw byte codec with the provided content type.
     *
     * @param contentType content type for payload
     * @return byte codec
     */
    public static @NonNull TaskContextCodec<byte[]> bytes(
        @NonNull String contentType
    ) {
        return new ByteArrayCodec(contentType);
    }

    private static final class Utf8StringCodec implements TaskContextCodec<String> {

        @Override
        public @NonNull TaskPayload encode(
            @NonNull String context
        ) {
            Objects.requireNonNull(context, "context");

            return new TaskPayload(context.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
        }

        @Override
        public @NonNull String decode(
            @NonNull TaskPayload payload
        ) {
            Objects.requireNonNull(payload, "payload");

            return new String(payload.data(), StandardCharsets.UTF_8);
        }
    }

    private static final class ByteArrayCodec implements TaskContextCodec<byte[]> {
        private final @NonNull String contentType;

        private ByteArrayCodec(
            @NonNull String contentType
        ) {
            Objects.requireNonNull(contentType, "contentType");
            Preconditions.checkArgument(!contentType.isBlank(), "contentType must not be blank");
            this.contentType = contentType;
        }

        @Override
        public @NonNull TaskPayload encode(
            @NonNull byte[] context
        ) {
            Objects.requireNonNull(context, "context");

            return new TaskPayload(context, contentType);
        }

        @Override
        public @NonNull byte[] decode(
            @NonNull TaskPayload payload
        ) {
            Objects.requireNonNull(payload, "payload");

            return payload.data();
        }
    }
}
