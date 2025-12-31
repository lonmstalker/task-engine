package io.lonmstalker.task.api.model;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Serialized task context.
 */
public record TaskPayload(
    @NonNull byte[] data,
    @NonNull String contentType
) {

    public TaskPayload {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(contentType, "contentType");
        Preconditions.checkArgument(!contentType.isBlank(), "contentType must not be blank");
        data = Arrays.copyOf(data, data.length);
    }

    /**
     * Returns payload bytes.
     *
     * @return payload bytes
     */
    @Override
    public @NonNull byte[] data() {
        return Arrays.copyOf(data, data.length);
    }
}
