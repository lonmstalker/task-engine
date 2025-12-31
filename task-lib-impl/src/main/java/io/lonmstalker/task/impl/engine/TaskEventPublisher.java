package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.store.TaskRecord;
import java.time.Instant;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.NonNull;

interface TaskEventPublisher extends AutoCloseable {

    boolean isEnabled();

    <T> T inSharedTransaction(
        @NonNull Supplier<T> action
    );

    void onRequestCreated(
        @NonNull TaskRecord record,
        @NonNull TaskPayload payload,
        @NonNull Instant now
    );

    void onDuplicate(
        @NonNull TaskRecord record,
        @NonNull TaskPayload payload,
        @NonNull Instant now,
        @NonNull TaskDefinition<?> definition
    );

    void onFinalized(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<?> definition,
        @NonNull Instant now
    );

    @Override
    void close();
}
