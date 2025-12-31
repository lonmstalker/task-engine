package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.event.TaskEventStore;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.store.TaskStore;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

final class TaskEventPublisherFactory {

    private TaskEventPublisherFactory() {
    }

    static @NonNull TaskEventPublisher create(
        @NonNull TaskStore store,
        @Nullable TaskEventStore eventStore,
        @NonNull Map<TaskType, TaskDefinition<?>> definitions
    ) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(definitions, "definitions");

        if (eventStore == null) {
            return new NoopTaskEventPublisher();
        }

        return new StoreTaskEventPublisher(store, eventStore, definitions);
    }
}
