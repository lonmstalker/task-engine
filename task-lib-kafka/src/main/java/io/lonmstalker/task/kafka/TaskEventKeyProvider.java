package io.lonmstalker.task.kafka;

import io.lonmstalker.task.api.event.TaskEventRecord;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Resolves Kafka message keys for task events.
 */
@FunctionalInterface
public interface TaskEventKeyProvider {

    @Nullable String keyFor(
        @NonNull TaskEventRecord record
    );

    static @NonNull TaskEventKeyProvider taskId() {
        return record -> record.taskId().value().toString();
    }

    static @NonNull TaskEventKeyProvider taskKey() {
        return record -> record.taskKey().value();
    }
}
