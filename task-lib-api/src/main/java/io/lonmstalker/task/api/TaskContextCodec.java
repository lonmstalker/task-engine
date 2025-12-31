package io.lonmstalker.task.api;

import io.lonmstalker.task.api.model.TaskPayload;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Serialization for task context.
 */
public interface TaskContextCodec<C> {

    /**
     * Encodes context into payload.
     *
     * @param context context to encode
     * @return payload
     */
    @NonNull TaskPayload encode(
        @NonNull C context
    );

    /**
     * Decodes context from payload.
     *
     * @param payload stored payload
     * @return decoded context
     */
    @NonNull C decode(
        @NonNull TaskPayload payload
    );
}
