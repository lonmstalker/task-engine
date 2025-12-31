package io.lonmstalker.task.impl.store;

import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.store.TaskRecord;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Optional extension for task stores that can load task chains efficiently.
 */
public interface TaskChainStore {

    @NonNull List<TaskRecord> loadChainRecords(
        @NonNull TaskId terminalTaskId
    );

    default @NonNull List<TaskId> loadDependentTaskIds(
        @NonNull TaskId rootTaskId
    ) {
        return List.of();
    }
}
