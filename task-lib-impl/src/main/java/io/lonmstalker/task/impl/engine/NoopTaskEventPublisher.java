package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.store.TaskRecord;
import java.time.Instant;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.NonNull;

final class NoopTaskEventPublisher implements TaskEventPublisher {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public <T> T inSharedTransaction(
        @NonNull Supplier<T> action
    ) {
        return action.get();
    }

    @Override
    public void onRequestCreated(
        @NonNull TaskRecord record,
        @NonNull TaskPayload payload,
        @NonNull Instant now
    ) {
    }

    @Override
    public void onDuplicate(
        @NonNull TaskRecord record,
        @NonNull TaskPayload payload,
        @NonNull Instant now,
        @NonNull TaskDefinition<?> definition
    ) {
    }

    @Override
    public void onFinalized(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<?> definition,
        @NonNull Instant now
    ) {
    }

    @Override
    public void close() {
    }
}
