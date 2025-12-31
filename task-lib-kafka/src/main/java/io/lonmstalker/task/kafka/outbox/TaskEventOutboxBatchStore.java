package io.lonmstalker.task.kafka.outbox;

import io.lonmstalker.task.api.annotations.Blocking;
import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventId;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Optional extension for batch loading outbox contexts.
 */
public interface TaskEventOutboxBatchStore {

    @Blocking
    @NonNull Map<TaskEventId, List<TaskEventContextEntry>> loadContexts(
        @NonNull List<TaskEventId> eventIds
    );
}
